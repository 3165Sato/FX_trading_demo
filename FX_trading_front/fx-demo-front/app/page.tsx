import { UsdJpyRateChart } from "./components/UsdJpyRateChart";

export default function Home() {
  return (
    <div className="min-h-screen bg-zinc-50 font-sans">
      <main className="mx-auto flex min-h-screen w-full max-w-6xl px-5 py-8 sm:px-8">
        <UsdJpyRateChart />
      </main>
    </div>
  );
}
