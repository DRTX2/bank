import { ApiError } from './api';

/**
 * The API speaks English (problem details for integrators); people read Spanish. Codes are the stable contract,
 * so the UI translates by code and keeps the server text only for codes it does not know yet.
 */
const byCode: Partial<Record<string, string>> = {
  INVALID_CREDENTIALS: 'Correo o contraseña incorrectos.',
  USER_LOCKED: 'Usuario bloqueado temporalmente por intentos fallidos. Inténtalo más tarde.',
  TENANT_SUSPENDED: 'Esta institución está suspendida.',
  RATE_LIMITED: 'Demasiados intentos seguidos. Espera un minuto.',
  MFA_INVALID: 'Código incorrecto o ya usado. Espera el siguiente código de tu app.',
  STEP_UP_FAILED: 'Código incorrecto o ya usado. Espera el siguiente código de tu app.',
  MFA_NOT_ENROLLED: 'Esta operación pide verificación en dos pasos. Actívala en Seguridad.',
  SEGREGATION_OF_DUTIES: 'Iniciaste esta operación: otra persona debe decidir (cuatro ojos).',
  PRIVILEGE_ESCALATION: 'No puedes otorgar permisos que tú no tienes.',
  WEAK_PASSWORD: 'La contraseña necesita de 10 a 128 caracteres, con letras y números, y no puede ser común.',
  IDEMPOTENCY_CONFLICT: 'Esta solicitud ya se envió con otros datos. Recarga e inténtalo de nuevo.',
  FORBIDDEN: 'Tu rol no permite esta operación.',
  NOT_FOUND: 'No encontramos lo que buscas.',
  CONFLICT: 'Ese registro ya existe.',
  INVALID_STATE: 'La operación ya no es posible en el estado actual.',
  DAY_ALREADY_CLOSED: 'El día de hoy ya está cerrado: los intereses se abonan una sola vez por día.',
  VALIDATION: 'Revisa los datos del formulario.',
};

/** ISO 20022 ExternalStatusReason1Code, as shown on transfers and pain.002 reports. */
export const isoReasons: Partial<Record<string, string>> = {
  AM04: 'Fondos insuficientes.',
  AM02: 'Supera el límite diario de la cuenta.',
  AM03: 'La moneda de las cuentas no coincide.',
  AC01: 'Número de cuenta inválido.',
  AC04: 'La cuenta está cerrada.',
  AC06: 'La cuenta está bloqueada.',
  FRAD: 'Bloqueada por sospecha de fraude.',
  DUPL: 'Mensaje duplicado.',
  FF01: 'Archivo con formato inválido.',
};

export function errorText(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return 'No se pudo completar la operación. Revisa tu conexión e inténtalo de nuevo.';
  }
  return (error.isoReason && isoReasons[error.isoReason]) ?? byCode[error.code] ?? error.message;
}
