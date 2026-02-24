import ChatWindow from "./ChatWindow";

export default function ChatPage() {
    return (
        <div className="animate-in fade-in duration-700 h-[calc(100vh-8rem)] flex flex-col">
            <header className="mb-6 flex-shrink-0">
                <h1 className="text-3xl font-extrabold tracking-tight text-white text-glow">
                    System Support Agent
                </h1>
                <p className="mt-1 text-slate-400">
                    Secure, hallucination-filtered conversational interface.
                </p>
            </header>

            <div className="flex-grow min-h-0">
                <ChatWindow />
            </div>
        </div>
    );
}
