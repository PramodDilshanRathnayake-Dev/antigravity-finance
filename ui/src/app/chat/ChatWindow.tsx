"use client";

import { useState, useRef, useEffect } from "react";
import axios from "axios";
import { Send, User, Bot, Loader2 } from "lucide-react";

interface Message {
    id: string;
    sender: 'user' | 'agent';
    text: string;
}

export default function ChatWindow() {
    const [messages, setMessages] = useState<Message[]>([
        {
            id: '1',
            sender: 'agent',
            text: "Welcome to Antigravity Finance. I am your trusted system agent. How can I assist you with your portfolio today?"
        }
    ]);
    const [input, setInput] = useState("");
    const [isTyping, setIsTyping] = useState(false);
    const messagesEndRef = useRef<HTMLDivElement>(null);

    const scrollToBottom = () => {
        messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
    };

    useEffect(() => {
        scrollToBottom();
    }, [messages]);

    const handleSend = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!input.trim()) return;

        const userMsg: Message = { id: Date.now().toString(), sender: 'user', text: input };
        setMessages(prev => [...prev, userMsg]);
        setInput("");
        setIsTyping(true);

        try {
            const res = await axios.post(
                "http://localhost:8080/api/v1/agent/chat",
                userMsg.text,
                { headers: { 'Content-Type': 'text/plain' } }
            );

            const agentMsg: Message = { id: (Date.now() + 1).toString(), sender: 'agent', text: res.data };
            setMessages(prev => [...prev, agentMsg]);
        } catch (error) {
            console.error("Chat error:", error);
            const errorMsg: Message = {
                id: (Date.now() + 1).toString(),
                sender: 'agent',
                text: "I am having trouble connecting to the core system right now. Your capital remains secure. Please try again later."
            };
            setMessages(prev => [...prev, errorMsg]);
        } finally {
            setIsTyping(false);
        }
    };

    return (
        <div className="glass-panel h-full flex flex-col overflow-hidden relative border border-white/10 ring-1 ring-white/5">
            {/* Messages Area */}
            <div className="flex-grow overflow-y-auto p-6 space-y-6">
                {messages.map((msg) => (
                    <div key={msg.id} className={`flex gap-4 ${msg.sender === 'user' ? 'flex-row-reverse' : ''}`}>
                        <div className={`w-8 h-8 shrink-0 rounded-full flex items-center justify-center ${msg.sender === 'user'
                                ? 'bg-sky-500/20 text-sky-400 border border-sky-400/30'
                                : 'bg-indigo-500/20 text-indigo-400 border border-indigo-400/30'
                            }`}>
                            {msg.sender === 'user' ? <User size={16} /> : <Bot size={16} />}
                        </div>

                        <div className={`max-w-[75%] rounded-2xl p-4 text-sm leading-relaxed ${msg.sender === 'user'
                                ? 'bg-sky-500/10 text-white rounded-tr-none border border-sky-500/20'
                                : 'bg-white/5 text-slate-200 rounded-tl-none border border-white/10'
                            }`}>
                            {msg.text}
                        </div>
                    </div>
                ))}

                {isTyping && (
                    <div className="flex gap-4">
                        <div className="w-8 h-8 rounded-full bg-indigo-500/20 text-indigo-400 border border-indigo-400/30 flex items-center justify-center">
                            <Bot size={16} />
                        </div>
                        <div className="bg-white/5 rounded-2xl rounded-tl-none p-4 flex items-center gap-2 border border-white/10">
                            <Loader2 size={16} className="animate-spin text-indigo-400" />
                            <span className="text-sm text-slate-400">Verifying with core system...</span>
                        </div>
                    </div>
                )}
                <div ref={messagesEndRef} />
            </div>

            {/* Input Area */}
            <div className="p-4 bg-slate-900/50 backdrop-blur-xl border-t border-white/10">
                <form onSubmit={handleSend} className="max-w-4xl mx-auto relative flex items-center">
                    <input
                        type="text"
                        value={input}
                        onChange={(e) => setInput(e.target.value)}
                        disabled={isTyping}
                        placeholder="Ask about your portfolio, risk exposure, or system status..."
                        className="w-full bg-white/5 border border-white/10 rounded-full py-3 pl-6 pr-14 text-sm text-white focus:outline-none focus:ring-2 focus:ring-sky-500/50 focus:border-transparent transition-all disabled:opacity-50 placeholder:text-slate-500"
                    />
                    <button
                        type="submit"
                        disabled={!input.trim() || isTyping}
                        className="absolute right-2 p-2 bg-sky-500 hover:bg-sky-400 text-white rounded-full transition-colors disabled:opacity-50 disabled:hover:bg-sky-500"
                    >
                        <Send size={16} />
                    </button>
                </form>
            </div>
        </div>
    );
}
