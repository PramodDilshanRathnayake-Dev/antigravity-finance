import type { Metadata } from 'next';
import { Inter } from 'next/font/google';
import './globals.css';
import Link from 'next/link';
import { Wallet, MessageSquare, Menu } from 'lucide-react';

const inter = Inter({ subsets: ['latin'], variable: '--font-inter' });

export const metadata: Metadata = {
  title: 'Antigravity Trading System',
  description: 'Autonomous financial investing bridging Crypto and Local Markets',
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
      <body className={`${inter.variable} antialiased bg-[#0f172a] text-slate-50 min-h-screen flex flex-col`}>
        {/* Animated Background layer */}
        <div className="animated-gradient-bg" />

        {/* Top Navigation */}
        <nav className="sticky top-0 z-50 glass-panel !rounded-none !border-t-0 !border-x-0 border-b border-b-white/10 px-6 py-4 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-8 h-8 rounded bg-gradient-to-br from-sky-400 to-indigo-500 shadow-lg shadow-sky-400/20 flex items-center justify-center">
              <span className="font-bold text-white text-lg">A</span>
            </div>
            <h1 className="text-xl font-bold tracking-tight text-white hidden sm:block">
              Antigravity <span className="text-sky-400 font-medium">Finance</span>
            </h1>
          </div>

          <div className="flex items-center gap-6">
            <Link href="/dashboard" className="flex items-center gap-2 text-slate-300 hover:text-white transition-colors">
              <Wallet className="w-5 h-5" />
              <span className="hidden sm:inline font-medium text-sm text-glow">Dashboard</span>
            </Link>
            <Link href="/chat" className="flex items-center gap-2 text-slate-300 hover:text-white transition-colors">
              <MessageSquare className="w-5 h-5" />
              <span className="hidden sm:inline font-medium text-sm text-glow">AI Agent</span>
            </Link>
            <button className="sm:hidden text-slate-300 hover:text-white">
              <Menu className="w-6 h-6" />
            </button>
          </div>
        </nav>

        {/* Main Content */}
        <main className="flex-grow container mx-auto px-4 py-8 sm:px-6 lg:px-8 max-w-7xl">
          {children}
        </main>
      </body>
    </html>
  );
}
