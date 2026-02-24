import WalletCard from "./WalletCard";
import RecentTrades from "./RecentTrades";

export default function DashboardPage() {
    return (
        <div className="space-y-8 animate-in fade-in duration-700">
            <header className="mb-10">
                <h1 className="text-3xl font-extrabold tracking-tight text-white sm:text-4xl text-glow">
                    Portfolio Overview
                </h1>
                <p className="mt-2 text-lg text-slate-400 max-w-2xl">
                    Real-time insights into your algorithmic trading capital. Initial deposits are strictly firewalled by the Antigravity System Agent.
                </p>
            </header>

            <WalletCard />
            <RecentTrades />
        </div>
    );
}
