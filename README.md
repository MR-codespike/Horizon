

# Horizon

Horizon is an Android AI assistant that can chat with you **and** operate your phone for you. Give it a goal in plain English — or say it out loud — and it will plan a sequence of taps, swipes, and typed input to get it done, narrating each step as it works.

## Features

**Chat with multiple AI providers**
Bring your own API key for any of three backends and switch between them freely:
- **Google AI Studio (Gemini)** — Gemini 2.5 Flash/Pro, 2.0 Flash Exp, 1.5 Flash/Pro
- **OpenRouter** — free-tier access to Gemini, Llama 3.3 70B, DeepSeek V3, Mistral 7B, Qwen 2.5 72B
- **Hugging Face Inference** — Llama 3.2 3B, Mistral 7B, Qwen 2.5 72B, Phi-3 Mini, Zephyr 7B

**Autonomous phone control agent**
Describe a goal — "open YouTube and search for Jetpack Compose tutorials," "add a new contact named Alex," "lower the media volume" — and Horizon's agent reads the current screen, decides the next action (tap a coordinate, type text, go back, go home), executes it through Android's Accessibility API, and loops until the goal is done. Every step is logged live so you can follow along.

**Simulator mode**
A built-in sandbox (Home, YouTube, Contacts, Add Contact, Settings, Lock screens) lets you try out the agent risk-free before ever touching your real device. Flip the **Simulator / Real OS** switch to move between the sandbox and live control of your phone.

**Voice control**
- Tap the mic to speak a one-off goal instead of typing it.
- Enable **Always-On Voice Wake** to trigger the agent hands-free any time by saying **"Horizon"** followed by your request — no need to open the app first.

**Live execution log**
Every agent run streams a step-by-step log of what it saw on screen, what it decided to do, and the result — so you always know what Horizon is doing on your behalf.

## How to use it

1. Open the app and pick a provider (Gemini, OpenRouter, or Hugging Face) and enter your API key in Settings.
2. Try Horizon out in **Simulator** mode first — type or say a goal like *"search YouTube for cats"* and watch the agent work through the sandbox screens.
3. When you're ready, flip the switch to **Real OS** and grant the Accessibility Service permission when prompted — this is what lets Horizon see your screen and perform taps/swipes on your actual device.
4. Type a goal, or tap the mic and speak one. To go fully hands-free, turn on **Always-On Voice Wake** and just say **"Horizon, [your request]"** any time.
5. Watch the live log as the agent executes each step, or interrupt/reset it at any time from the panel.

**Note:** The Accessibility Service is what gives Horizon the ability to control your device — it can see screen content and simulate taps and gestures. Only enable it if you trust the app, and you can revoke it at any time from Android's Accessibility settings.
