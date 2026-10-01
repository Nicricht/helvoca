# RecepVoz Voice Bake-Off

This harness compares provider-native Gemini voices without changing the tenant's production voice.

## Curated candidates

| Voice | Google trait |
| --- | --- |
| Leda | Youthful |
| Sadachbia | Lively |
| Laomedeia | Upbeat |
| Achird | Friendly |
| Aoede | Breezy |
| Sulafat | Warm |

## Safety and isolation

- The harness runs only through the explicit one-shot outbound certification path.
- The destination must match `TWILIO_CERTIFICATION_ALLOWED_TO`.
- The forbidden target check remains active.
- Every call keeps the automatic safety hangup.
- The candidate is signed into the Twilio Media Stream route, so changing it in transit invalidates the route.
- A bake-off call is pinned to Gemini and cannot fall back to another voice provider.
- Normal inbound calls keep the tenant's configured production voice.
- Phone microphone audio is ignored during the sample so every candidate receives the same input.
- Only `end_call` is published to the model; business tools are unavailable, so the sample cannot create bookings, orders, payments or messages.

## Fixed sample

Every candidate receives exactly the same three lines:

1. "Hola, gracias por llamar. Ya, cuéntame, ¿en qué te ayudo?"
2. "Sí, obvio. Tengo una hora mañana a las diez y media y otra a las doce. ¿Cuál te acomoda más?"
3. "Ya, súper. Quedó clarito. Gracias por llamar, que estés súper. Chao."

The model must finish the third line and then invoke `end_call`.

## Running one candidate

Keep normal production flags disabled, then set:

```text
TWILIO_CERTIFICATION_VOICE_OVERRIDE=Sadachbia
TWILIO_CERTIFICATION_CALL_ON_STARTUP=true
TWILIO_CERTIFICATION_DIRECTION=outbound-test
TWILIO_CERTIFICATION_MAX_SECONDS=35
```

After Twilio creates the call, immediately return `TWILIO_CERTIFICATION_CALL_ON_STARTUP=false`.

## Human scorecard

Score each candidate from 1 to 5 on:

- young-adult feminine impression;
- Chilean/Santiago feel;
- happiness and audible smile;
- naturalness;
- premium/commercial appeal;
- absence of call-center/IVR tone;
- clarity at fast-natural speed;
- "I would pay for this receptionist" overall reaction.

Provider labels are discovery hints only. The final production choice is made from real-call listening.


## Finalist conversation mode

The final comparison between shortlisted voices uses five deterministic receptionist turns instead of the shorter three-line sample:

1. "Hola, gracias por llamar. Ya, cuéntame, ¿en qué te ayudo?"
2. "Ya, perfecto. Entonces buscas una hora para mañana, ¿cierto?"
3. "Sí, obvio. Tengo una a las diez y media y otra a las doce. ¿Cuál te acomoda más?"
4. "Dale, las diez y media. Súper."
5. "Gracias por llamar, que estés súper. Chao."

This is a simulated conversation for voice evaluation only. Phone microphone audio remains ignored, the system advances the turns automatically, and only `end_call` is exposed. No availability lookup, booking, customer, payment, order or messaging tool is available, so the finalist test cannot create or mutate business data.

The three finalists are run with the same prompt, same five turns, same Gemini configuration and same telephony path. Only `TWILIO_CERTIFICATION_VOICE_OVERRIDE` changes between calls. The human listener chooses the production voice based on perceived gender, consistency, Chilean/Santiago character, naturalness, energy, audible smile and commercial appeal.


## Production winner

The September 27, 2026 human listening final selected **Sulafat** as the primary youthful female production voice.

- `seductive_female` resolves to Sulafat for Gemini Live.
- Legacy `Leda` and `Despina` selections migrate to the current youthful female profile.
- Leda and Laomedeia remain comparison/fallback candidates in the bake-off catalogue.
- The masculine profile and unrelated tenant voice profiles are unchanged.


## Cross-provider A/B

The provider comparison is separate from the six-voice Gemini bake-off above. It compares the actual production voice stacks while leaving normal inbound routing unchanged.

Use the existing one-shot outbound certification path and leave `TWILIO_CERTIFICATION_VOICE_OVERRIDE` blank. Pin one provider at a time:

```text
# Run A
TWILIO_CERTIFICATION_PROVIDER_OVERRIDE=gemini
TWILIO_CERTIFICATION_VOICE_OVERRIDE=

# Run B
TWILIO_CERTIFICATION_PROVIDER_OVERRIDE=openai-live
TWILIO_CERTIFICATION_VOICE_OVERRIDE=
```

Only `gemini` and `openai-live` are accepted. Unknown values fail closed. A provider pin is accepted only for `outbound-test`, never for inbound certification. When a provider is pinned, that certification call cannot fall back to the other provider.

A Gemini-specific voice candidate such as `Sulafat` cannot be combined with the OpenAI pin. For a provider comparison, use the tenant's normal voice profile. RecepVoz resolves that profile to the provider-specific voice automatically.

The call target allowlist, forbidden-target check, explicit startup opt-in, one-shot run gate and automatic safety hangup remain mandatory.

### Comparison scorecard

Run both providers with the same business, greeting, user scenario and tenant voice profile. Evaluate:

- time from the end of the caller turn to first audible response;
- naturalness and conversational flow;
- interruption / barge-in behavior;
- Spanish and Chilean conversational feel;
- pronunciation of names, times, prices and addresses;
- tool-use correctness and confirmation discipline;
- audio artifacts, clipping or robotic cadence;
- recovery after hesitation, correction or interruption;
- overall receptionist experience.

Do not change the production provider order based on a single pleasant sample. Repeat representative scenarios and retain the measured evidence.

### Verified production snapshot

On September 30, 2026, production logs show Gemini sessions using `gemini-3.8-live`. The normal provider order is `gemini,openai-live`. The OpenAI Live SIP integration has no production model override configured, so it uses the repository default `gpt-live-1`. This snapshot is operational evidence for the initial A/B and should be re-verified before future comparisons.
