/**
 * Human wording for risk-engine signal codes. Weights mirror services/risk-engine RiskRules (display only:
 * the engine's score is authoritative).
 */
export const RISK_SIGNALS: Record<string, { label: string; weight: number }> = {
  LARGE_AMOUNT: { label: 'Monto alto (≥ 5.000)', weight: 35 },
  ELEVATED_AMOUNT: { label: 'Monto elevado (≥ 2.000)', weight: 20 },
  AMOUNT_SPIKE: { label: 'Más de 5× su transferencia promedio', weight: 30 },
  VELOCITY_10M: { label: 'Varias transferencias en 10 minutos', weight: 30 },
  NEW_BENEFICIARY: { label: 'Primer pago a este beneficiario', weight: 10 },
  MULE_FAN_IN: { label: 'El destino recibe de muchas cuentas (posible cuenta mula)', weight: 25 },
  ODD_HOURS: { label: 'Horario inusual (00:00–05:00)', weight: 10 },
  ROUND_AMOUNT: { label: 'Monto redondo', weight: 5 },
  RISK_ENGINE_UNAVAILABLE: { label: 'Motor de riesgo no disponible: revisión manual', weight: 50 },
};

export const REVIEW_THRESHOLD = 40;
export const DECLINE_THRESHOLD = 70;
