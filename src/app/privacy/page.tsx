import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Slopevo Privacy Policy",
  description: "What the Slopevo app collects, where it is kept, who can see it and how to delete it.",
};

const CONTACT = "vernonweehongkoh.developer@outlook.com";

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return <section className="space-y-2">
    <h2 className="pt-2 text-lg font-semibold">{title}</h2>
    {children}
  </section>;
}

const muted = "text-sm leading-relaxed text-[var(--muted)]";
const list = `${muted} list-disc space-y-1 pl-5`;

export default function PrivacyPolicy() {
  return <article className="space-y-4">
    <header className="space-y-1">
      <h1 className="text-2xl font-bold">Slopevo Privacy Policy</h1>
      <p className={muted}>Last updated: 28 November 2026</p>
    </header>

    <p className={muted}>Slopevo is a personal app for tracking your weight, food photos and daily activity, and for sharing a summary with your trainer. It is run by Vernon Koh, Singapore (&ldquo;we&rdquo;, &ldquo;us&rdquo;). This policy explains what Slopevo collects, where it is kept, who can see it and how to delete it.</p>

    <Section title="Summary">
      <ul className={list}>
        <li>Your weigh-ins, food photos and progress photos are stored on your phone. They are not uploaded unless you create a trainer link that includes them.</li>
        <li>We use your sign-in details only to run your account.</li>
        <li>WHOOP data is read only after you connect WHOOP, and only to show it to you and in trainer links you create.</li>
        <li>There are no ads, no analytics or tracking tools and no crash-reporting services, and we do not sell your data.</li>
        <li>You can export everything, revoke any trainer link, disconnect WHOOP or delete your account from the app.</li>
      </ul>
    </Section>

    <Section title="1. Your account">
      <p className={muted}>When you sign in we receive your email address. If you sign in with Google, we also receive the name on your Google account. We use these only to sign you in and to show your name in the app. The sign-in session is kept encrypted on your phone.</p>
    </Section>

    <Section title="2. Data kept on your phone">
      <ul className={list}>
        <li>Weigh-ins (date and weight)</li>
        <li>Food photos and progress photos you take or choose</li>
        <li>Your settings: goal, target weight, step goal, height, units, theme and reminder times</li>
      </ul>
      <p className={muted}>This data stays on your phone. If Android backup is turned on, Android may include it in your device backup to your Google account; that backup is controlled by you and Google, not by us. Your sign-in session is excluded from backups. Reminders are scheduled on your phone; Slopevo does not use push-notification servers.</p>
    </Section>

    <Section title="3. WHOOP data">
      <p className={muted}>If you connect your WHOOP account, Slopevo reads the data you allow, through WHOOP&rsquo;s official API:</p>
      <ul className={list}>
        <li>cycles (day strain and steps)</li>
        <li>recovery</li>
        <li>sleep</li>
        <li>workouts</li>
        <li>profile (name and email on your WHOOP account)</li>
        <li>body measurements (height, weight and maximum heart rate)</li>
      </ul>
      <p className={muted}>We use this data only to show your activity in the app and in trainer links you create. To keep the connection working, the access tokens WHOOP issues are stored on our server and used only to fetch your data. We never receive your WHOOP password. You can disconnect WHOOP at any time in the app, which deletes the stored tokens. You can also revoke access from your WHOOP account.</p>
    </Section>

    <Section title="4. Trainer links">
      <p className={muted}>When you create a link for your trainer, Slopevo uploads to our server:</p>
      <ul className={list}>
        <li>the trainer name you type</li>
        <li>the period (a month or a year) and the link&rsquo;s end date</li>
        <li>a summary of that period, with only the parts you chose: activity (daily steps, sleep, strain and recovery), weight (your weigh-ins, with your first and last progress photo) and food (your food photos)</li>
      </ul>
      <p className={muted}>Anyone with the link can see that summary until it ends or you revoke it, so share it only with your trainer. The link&rsquo;s address is kept only on your phone; our server stores only a scrambled fingerprint of it (a one-way hash), so the address cannot be rebuilt from our records.</p>
      <p className={muted}>When you revoke a link, its record and photos are deleted from our server. When a link expires, it stops working at once, but its record and photos stay in private storage until you revoke the link or delete your account.</p>
    </Section>

    <Section title="Who processes your data">
      <p className={muted}>We use these providers only to run Slopevo:</p>
      <ul className={list}>
        <li><strong>Supabase</strong>: sign-in, sign-in emails, the database and photo storage for trainer links, and WHOOP token storage</li>
        <li><strong>Google</strong>: &ldquo;Sign in with Google&rdquo;, if you use it</li>
        <li><strong>Vercel</strong>: hosts the web page your trainer opens</li>
        <li><strong>WHOOP</strong>: source of your WHOOP data, if you connect it</li>
      </ul>
      <p className={muted}>Their servers may be in a country other than yours. We do not share your data with anyone else, except when the law requires it.</p>
    </Section>

    <Section title="Your choices and rights">
      <ul className={list}>
        <li>Export: Profile › Export gives you a ZIP file with your weigh-ins, food log and photos.</li>
        <li>Revoke a link: Share with trainer › Revoke.</li>
        <li>Disconnect WHOOP: in the app or in your WHOOP account.</li>
        <li>Delete your account: Profile › Delete account. This deletes your account, all your trainer links and their photos from our server, then erases Slopevo&rsquo;s data on your phone.</li>
        <li>You can ask us to access, correct or delete your data at <a href={`mailto:${CONTACT}`} className="underline">{CONTACT}</a>.</li>
      </ul>
    </Section>

    <Section title="Security">
      <p className={muted}>Connections use HTTPS. Your sign-in session is encrypted with a key held in your phone&rsquo;s secure hardware. Uploaded photos sit in private storage that only you can read, except through an active trainer link.</p>
    </Section>

    <Section title="Children">
      <p className={muted}>Slopevo is not meant for children under 13, and we do not knowingly collect their data.</p>
    </Section>

    <Section title="Changes">
      <p className={muted}>If we change this policy, we will update the date above and, for important changes, tell you in the app.</p>
    </Section>

    <Section title="Contact">
      <p className={muted}>Vernon Koh, Singapore. <a href={`mailto:${CONTACT}`} className="underline">{CONTACT}</a></p>
    </Section>
  </article>;
}
