import { getStore } from "@netlify/blobs";

export default async (req) => {
  const url = new URL(req.url);
  const user  = url.searchParams.get("user");
  const month = url.searchParams.get("month"); // YYYY-MM

  if (!user || !month || !/^\d{4}-\d{2}$/.test(month)) {
    return new Response(JSON.stringify({ error: "Missing or invalid user/month" }), {
      status: 400, headers: { "Content-Type": "application/json" }
    });
  }

  const key = `zeit_${user}_${month}`;

  try {
    const store = getStore({ name: "reisekosten", consistency: "strong" });

    if (req.method === "GET") {
      const data = await store.get(key, { type: "json" });
      return new Response(JSON.stringify(data ?? []), {
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
  } catch (e) {
    return new Response(JSON.stringify({ error: e.message }), {
      status: 500, headers: { "Content-Type": "application/json" }
    });
  }

  return new Response("Method not allowed", { status: 405 });
};

export const config = { path: "/api/zeit" };
