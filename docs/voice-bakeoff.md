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
- Leda and Laomedeia remain useful comparison/fallback candidates in the bake-off catalogue.
- The change does not alter the masculine profile or unrelated tenant voice profiles.
