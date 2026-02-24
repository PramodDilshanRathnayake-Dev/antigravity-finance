"use client";

import { useEffect, useState } from "react";
import { Shield, TrendingUp, DollarSign, Activity } from "lucide-react";
import axios from "axios";

export default function WalletCard() {
    const [data, setData] = useState({
        protectedCapitalBase: 0,
        accumulatedProfit: 0,
        totalWithdrawals: 0,
        totalValue: 0,
    });
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        const fetchData = async () => {
            try {
                const res = await axios.get("http://localhost:8080/api/v1/portfolio/usr_001");
                const withdrawRes = await axios.get("http://localhost:8080/api/v1/portfolio/usr_001/withdrawals");

                setData({
                    protectedCapitalBase: res.data.protectedCapitalBase,
                    accumulatedProfit: res.data.accumulatedProfit,
                    totalValue: res.data.protectedCapitalBase + res.data.accumulatedProfit,
                    totalWithdrawals: withdrawRes.data.totalWithdrawals
                });
            } catch (error) {
                console.error("Error fetching portfolio:", error);
            } finally {
                setLoading(false);
            }
        };

        fetchData();
        const interval = setInterval(fetchData, 10000); // Polling every 10s
        return () => clearInterval(interval);
    }, []);

    const formatCurrency = (val: number) => {
        return new Intl.NumberFormat('en-LK', { style: 'currency', currency: 'LKR' }).format(val);
    };

    if (loading) return <div className="glass-panel p-6 animate-pulse h-48 rounded-2xl" />;

    return (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">

            {/* Protected Capital */}
            <div className="glass-panel p-6 flex flex-col items-start hover:border-sky-400/50 transition-colors duration-300">
                <div className="w-10 h-10 rounded-full bg-sky-400/10 flex items-center justify-center mb-4 border border-sky-400/20">
                    <Shield className="w-5 h-5 text-sky-400" />
                </div>
                <p className="text-sm font-medium text-slate-400 mb-1">Protected Capital Base</p>
                <h3 className="text-2xl font-bold tracking-tight text-glow">{formatCurrency(data.protectedCapitalBase)}</h3>
                <span className="text-xs text-emerald-400 mt-2 flex items-center gap-1">
                    <Shield className="w-3 h-3" /> Fully Firewalled
                </span>
            </div>

            {/* Accumulated Profit */}
            <div className="glass-panel p-6 flex flex-col items-start hover:border-emerald-400/50 transition-colors duration-300">
                <div className="w-10 h-10 rounded-full bg-emerald-400/10 flex items-center justify-center mb-4 border border-emerald-400/20">
                    <TrendingUp className="w-5 h-5 text-emerald-400" />
                </div>
                <p className="text-sm font-medium text-slate-400 mb-1">Accumulated Profit</p>
                <h3 className="text-2xl font-bold tracking-tight text-white">{formatCurrency(data.accumulatedProfit)}</h3>
                <span className="text-xs text-emerald-400/70 mt-2">Available for withdrawal / risk</span>
            </div>

            {/* Withdrawals */}
            <div className="glass-panel p-6 flex flex-col items-start hover:border-rose-400/50 transition-colors duration-300">
                <div className="w-10 h-10 rounded-full bg-rose-400/10 flex items-center justify-center mb-4 border border-rose-400/20">
                    <DollarSign className="w-5 h-5 text-rose-400" />
                </div>
                <p className="text-sm font-medium text-slate-400 mb-1">Total Withdrawals</p>
                <h3 className="text-2xl font-bold tracking-tight text-white">{formatCurrency(data.totalWithdrawals)}</h3>
            </div>

            {/* Total Value */}
            <div className="glass-panel p-6 flex flex-col items-start relative overflow-hidden ring-1 ring-white/10 hover:ring-indigo-400/50 transition-shadow">
                <div className="absolute -right-4 -top-4 w-24 h-24 bg-indigo-500/20 blur-2xl rounded-full" />
                <div className="w-10 h-10 rounded-full bg-indigo-400/10 flex items-center justify-center mb-4 border border-indigo-400/20 z-10">
                    <Activity className="w-5 h-5 text-indigo-400" />
                </div>
                <p className="text-sm font-medium text-slate-400 mb-1 z-10">Total Portfolio Value</p>
                <h3 className="text-2xl font-bold tracking-tight text-white z-10">{formatCurrency(data.totalValue)}</h3>
            </div>

        </div>
    );
}
