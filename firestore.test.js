const { initializeTestEnvironment, assertFails, assertSucceeds } = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

const PROJECT_ID = "demo-no-project";
let testEnv;

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules: fs.readFileSync("firestore.rules", "utf8"),
      host: "127.0.0.1",
      port: 8085
    }
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

test("unauthenticated user cannot read members", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("members").get());
});

test("authenticated user can read members", async () => {
  const authDb = testEnv.authenticatedContext("user123", { email: "user@example.com" }).firestore();
  await assertSucceeds(authDb.collection("members").get());
});

test("unauthenticated user cannot write savings", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("savings").add({
    memberId: "m1",
    amount: 100,
    status: "PENDING"
  }));
});

test("admin user can create and update settings", async () => {
  // admin user with role in users doc or admin email
  const adminDb = testEnv.authenticatedContext("admin1", { email: "netai.nemai@gmail.com" }).firestore();
  await assertSucceeds(adminDb.collection("appSettings").doc("main").set({
    orgName: "যাদবপুর যুব সমাজ সমিতি",
    currency: "BDT"
  }));
});

test("unauthenticated user cannot access fines and admin can create fines", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("fines").get());

  const adminDb = testEnv.authenticatedContext("admin1", { email: "netai.nemai@gmail.com" }).firestore();
  await assertSucceeds(adminDb.collection("fines").doc("fine_sav_m1_05-03-26").set({
    memberId: "m1",
    memberName: "রহিম",
    fineType: "SAVINGS",
    amount: 10,
    isPaid: false
  }));
});

test("field officer can create entries but cannot edit or delete existing entries", async () => {
  const adminDb = testEnv.authenticatedContext("admin1", { email: "netai.nemai@gmail.com" }).firestore();
  await adminDb.collection("users").doc("officer1").set({
    role: "FIELD_OFFICER",
    email: "officer@example.com"
  });
  await adminDb.collection("savings").doc("m1_05-03-26").set({
    memberId: "m1",
    amount: 100,
    status: "APPROVED"
  });
  await adminDb.collection("fines").doc("fine_1").set({
    memberId: "m1",
    amount: 10,
    isPaid: false
  });

  const officerDb = testEnv.authenticatedContext("officer1", { email: "officer@example.com" }).firestore();
  // Officer can create new savings entry
  await assertSucceeds(officerDb.collection("savings").doc("m1_12-03-26").set({
    memberId: "m1",
    amount: 100,
    status: "PENDING"
  }));
  // Officer cannot update or delete existing savings/fines
  await assertFails(officerDb.collection("savings").doc("m1_05-03-26").update({ amount: 200 }));
  await assertFails(officerDb.collection("savings").doc("m1_05-03-26").delete());
  await assertFails(officerDb.collection("fines").doc("fine_1").update({ isPaid: true }));
  await assertFails(officerDb.collection("fines").doc("fine_1").delete());

  // Admin can update and delete
  await assertSucceeds(adminDb.collection("fines").doc("fine_1").update({ isPaid: true }));
  await assertSucceeds(adminDb.collection("fines").doc("fine_1").delete());
});

test("field officer can trigger adminNotifications create but only admin can manage tokens and read notifications", async () => {
  const adminDb = testEnv.authenticatedContext("admin1", { email: "netai.nemai@gmail.com" }).firestore();
  await adminDb.collection("users").doc("officer1").set({
    role: "FIELD_OFFICER",
    email: "officer@example.com"
  });
  await assertSucceeds(adminDb.collection("adminFcmTokens").doc("tok_1").set({
    token: "fcm_abc_123",
    userId: "admin1"
  }));

  const officerDb = testEnv.authenticatedContext("officer1", { email: "officer@example.com" }).firestore();
  await assertFails(officerDb.collection("adminFcmTokens").doc("tok_2").set({ token: "hacked" }));
  await assertSucceeds(officerDb.collection("adminNotifications").doc("n1").set({
    title: "নতুন সঞ্চয় অনুমোদন",
    body: "রহিম - ৳১০০",
    targetScreen: "APPROVALS",
    deepLink: "ekotasamiti://approvals"
  }));
  await assertFails(officerDb.collection("adminNotifications").get());
  await assertFails(officerDb.collection("adminNotifications").doc("n1").update({ isRead: true }));
  await assertSucceeds(adminDb.collection("adminNotifications").doc("n1").update({ isRead: true }));
});
