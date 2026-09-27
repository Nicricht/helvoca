#!/usr/bin/env bash
set -euo pipefail

mvn --batch-mode --no-transfer-progress \
  -Dtest=GeminiLiveVoiceSessionTest,ClosingConversationCertificationPackTest,BookingConversationCertificationPackTest,BookingConversationStateMachineTest,RecepVozConversationPolicyServiceTest,ConversationQualityGoldenScenarioTest,RealtimeEndCallToolTest,TwilioMediaStreamHandlerTest,TwilioVoiceTransportSessionTest \
  test
