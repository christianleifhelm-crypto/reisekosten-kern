import { getStore } from "@netlify/blobs";

const DEFAULT_CITIES = [
  "Ahlen","Ahaus","Beckum","Beelen","Bielefeld","Diestedde","Drensteinfurt",
  "Ennigerloh","Erwitte","Everswinkel","Freckenhorst","Greffen","Hamm",
  "Harsewinkel","Herzebrock-Clarholz","Milte","Münster","Oelde","Osnabrück",
  "Ostbevern","Österwiehe","Rheda-Wiedenbrück","Rietberg","Sassenberg",
  "Schloß Holte-Stukenbrock","Sendenhorst","Telgte","Versmold","Wadersloh",
  "Warendorf","Werne"
];

export default async (req) => {
  const url = new URL(req.url);
  const key = url.searchParams.get("key");

  if (!key || !["users", "cities"].includes(key)) {
    return new Response(JSON.stringify({ error: "Invalid key" }), {
      status: 400, headers: { "Content-Type": "application/json" }
    });
  }

  try {
    const store = getStore({ name: "reisekosten", consistency: "strong" });

    if (req.method === "GET") {
      let data = await store.get(key, { type: "json" });
      if (data === null && key === "cities") {
        data = DEFAULT_CITIES;
        await store.setJSON("cities", data);
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
  } catch(e) {
    return new Response(JSON.stringify({ error: e.message }), {
      status: 500, headers: { "Content-Type": "application/json" }
    });
  }

  return new Response("Method not allowed", { status: 405 });
};

export const config = { path: "/api/data" };
