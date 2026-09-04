// Loescht das eigene Konto samt allem, was daran haengt.
//
// Apple (Richtlinie 5.1.1) und Google verlangen das fuer jede App mit
// Registrierung: Wer ein Konto anlegen kann, muss es in der App auch wieder
// loswerden - ohne E-Mail an den Betreiber.
//
// Laeuft nur mit gueltiger Anmeldung (verify_jwt an). Geloescht wird genau
// der Nutzer, dessen Token mitkommt; eine Kennung im Aufruf gibt es bewusst
// nicht. Die Datenbank raeumt ueber "on delete cascade" hinterher:
// members -> rsvps, device_tokens, invites. Gruppen, in denen danach niemand
// mehr ist, werden mitgeloescht - sonst bliebe der Gruppenname zurueck.

import { createClient } from "https://esm.sh/@supabase/supabase-js@2.47.10";

function reply(body: Record<string, unknown>): Response {
  return Response.json({ ok: !body.error, ...body });
}

Deno.serve(async (request) => {
  try {
    const token = (request.headers.get("Authorization") ?? "").replace(/^Bearer\s+/i, "");
    if (!token) return reply({ error: "Nicht angemeldet" });

    const admin = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    );

    const { data: userData, error: userError } = await admin.auth.getUser(token);
    const user = userData?.user;
    if (userError || !user) return reply({ error: "Nicht angemeldet" });

    // Gruppen merken, bevor die Mitgliedschaften verschwinden.
    const { data: memberships } = await admin
      .from("members")
      .select("group_id")
      .eq("user_id", user.id);
    const groupIds = [...new Set((memberships ?? []).map((m) => m.group_id))];

    const { error: deleteError } = await admin.auth.admin.deleteUser(user.id);
    if (deleteError) {
      console.error("deleteUser", deleteError);
      return reply({ error: "Das Konto konnte nicht gelöscht werden. Bitte später erneut versuchen." });
    }

    for (const groupId of groupIds) {
      const { count } = await admin
        .from("members")
        .select("id", { count: "exact", head: true })
        .eq("group_id", groupId);
      if (count === 0) {
        await admin.from("groups").delete().eq("id", groupId);
      }
    }

    return reply({ deleted: true });
  } catch (error) {
    console.error("account-delete", error);
    return reply({ error: "Das Konto konnte nicht gelöscht werden. Bitte später erneut versuchen." });
  }
});
