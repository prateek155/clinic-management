export default function LoadingScreen({ label = 'Loading your secure workspace' }) {
  return <main className="grid min-h-screen place-items-center"><div className="flex items-center gap-3 text-sm font-medium text-slate-500"><span className="size-2 animate-pulse rounded-full bg-teal-600" />{label}</div></main>;
}
