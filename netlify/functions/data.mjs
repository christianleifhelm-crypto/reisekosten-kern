import { getStore } from "@netlify/blobs";

const DEFAULT_CITIES = [
  "Ahlen","Ahaus","Beckum","Beelen","Bielefeld","Diestedde","Drensteinfurt",
  "Ennigerloh","Erwitte","Everswinkel","Freckenhorst","Greffen","Hamm",
  "Harsewinkel","Herzebrock-Clarholz","Milte","Münster","Oelde","Osnabrück",
  "Ostbevern","Österwiehe","Rheda-Wiedenbrück","Rietberg","Sassenberg",
  "Schloß Holte-Stukenbrock","Sendenhorst","Telgte","Versmold","Wadersloh",
  "Warendorf","Werne"
];

function getSharedStore() {
  return getStore({ name: "reisekosten-shared", consistency: "strong" });
}

export default async (req) => {
  const store = getSharedStore();
  const url = new URL(req.url);
  const key = url.searchParams.get("key");

  if (!key || !["users", "cities"].includes(key)) {
    return new Response(JSON.stringify({ error: "Invalid key" }), {
      status: 400, headers: { "Content-Type": "application/json" }
    });
  }

  if (req.method === "GET") {
    let data = await store.get(key, { type: "json" });
    // First time: initialize cities with defaults
    if (data === null && key === "cities") {
      await store.setJSON("cities", DEFAULT_CITIES);
      data = DEFAULT_CITIES;
    }
    if (data === null) data = [];
    return new Response(JSON.stringify(data), {
      status: 200, headers: { "Content-Type": "application/json" }
    });
  }

  if (req.method === "POST") {
    const body = await req.json();
    await store.setJSON(key, body);
    return new Response(JSON.stringify({ ok: true }), {
      status: 200, headers: { "Content-Type": "application/json" }
    });
  }

  return new Response("Method not allowed", { status: 405 });
};

export const config = { path: "/api/data" };
