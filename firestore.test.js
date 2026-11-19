const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const CHARLIE_UID = "charlie_789";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate(msOffset = 2000) {
  return new Date(Date.now() - msOffset);
}

function makePublicProfile(uid, username, distanceMeters = 5200) {
  const now = pastDate(2000);
  return {
    userId: uid,
    username,
    usernameLower: username.toLowerCase(),
    leaderboardVisible: true,
    totalDistanceMeters: distanceMeters,
    weeklyDistanceMeters: distanceMeters,
    monthlyDistanceMeters: distanceMeters,
    yearlyDistanceMeters: distanceMeters,
    totalWorkouts: 3,
    weeklyWorkouts: 2,
    monthlyWorkouts: 3,
    yearlyWorkouts: 3,
    totalMovingTimeMillis: 1800000,
    averagePaceSecPerKm: 346,
    bestPaceSecPerKm: 310,
    totalSteps: 12000,
    weekKey: "2026-W47",
    monthKey: "2026-11",
    yearKey: "2026",
    createdAt: now,
    updatedAt: now,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated users cannot read or write any collection", async () => {
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection("users").doc(ALICE_UID).get());
  await assertFails(unauth.collection("public_profiles").where("leaderboardVisible", "==", true).limit(10).get());
  await assertFails(unauth.collection("usernames").doc("runneralice").get());
  await assertFails(unauth.collection("friend_requests").where("memberUids", "array-contains", ALICE_UID).get());
});

test("Private user profile: owner read/write succeeds, cross-user PII read & shadow fields fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastDate(3000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: "alice@example.com",
      dailyStepGoal: 8000,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Bob cannot read Alice's private email doc
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());

  // Shadow field injection must fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: pastDate(500),
    })
  );

  // Valid goal update succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      dailyStepGoal: 10000,
      updatedAt: pastDate(500),
    })
  );
});

test("Atomic unique username reservation prevents duplicate nicknames and protects public_profiles", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastDate(3000);

  // Alice atomically claims "런닝왕" + creates public_profile
  const batch = aliceDb.batch();
  batch.set(aliceDb.collection("usernames").doc("런닝왕"), {
    userId: ALICE_UID,
    username: "런닝왕",
    createdAt: ts,
  });
  batch.set(
    aliceDb.collection("public_profiles").doc(ALICE_UID),
    makePublicProfile(ALICE_UID, "런닝왕", 8400)
  );
  await assertSucceeds(batch.commit());

  // Bob cannot claim the same username "런닝왕"
  await assertFails(
    bobDb.collection("usernames").doc("런닝왕").set({
      userId: BOB_UID,
      username: "런닝왕",
      createdAt: ts,
    })
  );

  // Bob cannot spoof Alice's username in Bob's public_profile
  await assertFails(
    bobDb.collection("public_profiles").doc(BOB_UID).set(makePublicProfile(BOB_UID, "런닝왕", 9999))
  );

  // Unfiltered public_profiles query without limit <= 50 & leaderboardVisible fails
  await assertFails(bobDb.collection("public_profiles").get());

  // Aligned leaderboard query succeeds
  await assertSucceeds(
    bobDb.collection("public_profiles").where("leaderboardVisible", "==", true).limit(50).get()
  );

  // Standalone stats update (refreshPublicProfileStats) succeeds without touching usernames
  await assertSucceeds(
    aliceDb.collection("public_profiles").doc(ALICE_UID).update({
      totalDistanceMeters: 10500,
      weeklyDistanceMeters: 10500,
      totalWorkouts: 4,
      updatedAt: pastDate(500),
    })
  );

  // Atomic nickname rename: delete old username, create new username, update public_profiles
  const renameBatch = aliceDb.batch();
  renameBatch.delete(aliceDb.collection("usernames").doc("런닝왕"));
  renameBatch.set(aliceDb.collection("usernames").doc("질주본능"), {
    userId: ALICE_UID,
    username: "질주본능",
    createdAt: pastDate(300),
  });
  renameBatch.update(aliceDb.collection("public_profiles").doc(ALICE_UID), {
    username: "질주본능",
    usernameLower: "질주본능",
    updatedAt: pastDate(300),
  });
  await assertSucceeds(renameBatch.commit());
});

test("User workouts and daily steps: owner CRUD and query parity succeed, cross-user fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastDate(4000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("workouts").doc("w_1").set({
      userId: ALICE_UID,
      type: "RUN",
      startedAtMillis: 100000,
      endedAtMillis: 400000,
      movingTimeMillis: 300000,
      distanceMeters: 1200.5,
      averagePaceSecPerKm: 250,
      caloriesBurned: 90,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("dailySteps").doc("2026-11-18").set({
      userId: ALICE_UID,
      dateKey: "2026-11-18",
      steps: 6420,
      goalSteps: 8000,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Owner queries succeed
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("workouts").orderBy("startedAtMillis", "desc").get()
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("dailySteps").orderBy("dateKey", "desc").limit(366).get()
  );

  // Cross-user access fails
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("workouts").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("dailySteps").get()
  );
});

test("Friend requests: send, recipient accept, terminal lock, and member-only reads", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const ts = pastDate(4000);

  // Setup Alice & Bob public profiles
  const aliceBatch = aliceDb.batch();
  aliceBatch.set(aliceDb.collection("usernames").doc("alice_run"), {
    userId: ALICE_UID,
    username: "Alice_Run",
    createdAt: ts,
  });
  aliceBatch.set(aliceDb.collection("public_profiles").doc(ALICE_UID), makePublicProfile(ALICE_UID, "Alice_Run", 5000));
  await assertSucceeds(aliceBatch.commit());

  const bobBatch = bobDb.batch();
  bobBatch.set(bobDb.collection("usernames").doc("bob_run"), {
    userId: BOB_UID,
    username: "Bob_Run",
    createdAt: ts,
  });
  bobBatch.set(bobDb.collection("public_profiles").doc(BOB_UID), makePublicProfile(BOB_UID, "Bob_Run", 7500));
  await assertSucceeds(bobBatch.commit());

  const reqId = `${ALICE_UID}_${BOB_UID}`;
  await assertSucceeds(
    aliceDb.collection("friend_requests").doc(reqId).set({
      fromUid: ALICE_UID,
      fromUsername: "Alice_Run",
      toUid: BOB_UID,
      toUsername: "Bob_Run",
      status: "PENDING",
      memberUids: [ALICE_UID, BOB_UID],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Sender Alice cannot self-accept
  await assertFails(
    aliceDb.collection("friend_requests").doc(reqId).update({
      status: "ACCEPTED",
      updatedAt: pastDate(500),
    })
  );

  // Outsider Charlie cannot read or list Alice/Bob friend requests
  await assertFails(charlieDb.collection("friend_requests").doc(reqId).get());
  await assertFails(charlieDb.collection("friend_requests").where("memberUids", "array-contains", ALICE_UID).get());

  // Recipient Bob accepts
  await assertSucceeds(
    bobDb.collection("friend_requests").doc(reqId).update({
      status: "ACCEPTED",
      updatedAt: pastDate(500),
    })
  );

  // Terminal state lock: cannot revert ACCEPTED back to PENDING
  await assertFails(
    bobDb.collection("friend_requests").doc(reqId).update({
      status: "PENDING",
      updatedAt: pastDate(200),
    })
  );

  // Member query succeeds
  await assertSucceeds(
    aliceDb.collection("friend_requests").where("memberUids", "array-contains", ALICE_UID).get()
  );
});
