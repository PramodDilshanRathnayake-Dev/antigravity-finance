"use client";

import { useEffect, useState } from "react";
import axios from "axios";
import { ArrowUpRight, ArrowDownRight, Clock } from "lucide-react";

interface Trade {
    id: string;
    assetId: string;
    action: string;
    amountAllocated: number;
    executionPrice: number;
    strategyUsed: string;
    cvarExposure: number;
    timestamp: string;
}

export default function RecentTrades() {
    const [trades, setTrades] = useState<Trade[]>([]);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        const fetchTrades = async () => {
            try {
                const res = await axios.get("http://localhost:8080/api/v1/trades/usr_001");
                setTrades(res.data);
            } catch (error) {
                console.error("Error fetching trades:", error);
            } finally {
                setLoading(false);
            }
        };

        fetchTrades();
        const interval = setInterval(fetchTrades, 10000);
        return () => clearInterval(interval);
    }, []);

    if (loading) return <div className="glass-panel h-64 w-full mt-8 animate-pulse rounded-2xl" />;

    return (
        <div className="glass-panel p-6 mt-8">
            <div className="flex items-center justify-between mb-6 border-b border-white/5 pb-4">
                <div>
                    <h2 className="text-lg font-semibold text-white">Recent Executions</h2>
                    <p className="text-sm text-slate-400">Algorithmic trades executed on Local Market</p>
                </div>
                <div className="bg-sky-400/10 border border-sky-400/20 px-3 py-1 rounded-full flex items-center gap-2">
                    <span className="relative flex h-2 w-2">
                        <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-sky-400 opacity-75"></span>
                        <span className="relative inline-flex rounded-full h-2 w-2 bg-sky-500"></span>
                    </span>
                    <span className="text-xs font-medium tracking-wide text-sky-400 uppercase">System Active</span>
                </div>
            </div>

            {trades.length === 0 ? (
                <div className="py-12 flex flex-col items-center justify-center text-slate-500">
                    <Clock className="w-8 h-8 mb-3 opacity-50" />
                    <p>Awaiting first trade execution...</p>
                </div>
            ) : (
                <div className="overflow-x-auto">
                    <table className="w-full text-left border-collapse">
                        <thead>
                            <tr className="text-xs uppercase tracking-wider text-slate-400 border-b border-white/10">
                                <th className="pb-3 px-4 font-medium">Asset</th>
                                <th className="pb-3 px-4 font-medium">Action</th>
                                <th className="pb-3 px-4 font-medium">Price</th>
                                <th className="pb-3 px-4 font-medium">Allocated</th>
                                <th className="pb-3 px-4 font-medium">Strategy</th>
                                <th className="pb-3 px-4 font-medium text-right">Time</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-white/5">
                            {trades.slice(0, 10).map((t) => (
                                <tr key={t.id} className="hover:bg-white/5 transition-colors group">
                                    <td className="py-4 px-4 font-medium text-slate-200">{t.assetId}</td>
                                    <td className="py-4 px-4">
                                        <span className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-xs font-medium border ${t.action === 'BUY'
                                                ? 'bg-emerald-400/10 text-emerald-400 border-emerald-400/20'
                                                : 'bg-rose-400/10 text-rose-400 border-rose-400/20'
                                            }`}>
                                            {t.action === 'BUY' ? <ArrowUpRight className="w-3 h-3" /> : <ArrowDownRight className="w-3 h-3" />}
                                            {t.action}
                                        </span>
                                    </td>
                                    <td className="py-4 px-4 text-slate-300">
                                        {new Intl.NumberFormat('en-LK', { style: 'currency', currency: 'LKR' }).format(t.executionPrice)}
                                    </td>
                                    <td className="py-4 px-4 text-slate-300">
                                        {new Intl.NumberFormat('en-LK', { style: 'currency', currency: 'LKR' }).format(t.amountAllocated)}
                                    </td>
                                    <td className="py-4 px-4">
                                        <span className="text-xs text-slate-400 uppercase font-mono tracking-tight">{t.strategyUsed}</span>
                                    </td>
                                    <td className="py-4 px-4 text-right text-sm text-slate-500">
                                        {new Date(t.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })}
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            )}
        </div>
    );
}
