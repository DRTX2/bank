import { describe, expect, it } from 'vitest';

import { ApiError } from '../lib/api';
import { errorText } from '../lib/errors';

describe('errorText', () => {
  it('prefers the ISO reason, then the error code, then the server text', () => {
    expect(errorText(new ApiError(422, 'INSUFFICIENT_FUNDS', 'Insufficient funds', 'AM04'))).toBe(
      'Fondos insuficientes.',
    );
    expect(errorText(new ApiError(401, 'INVALID_CREDENTIALS', 'Invalid credentials'))).toBe(
      'Correo o contraseña incorrectos.',
    );
    expect(errorText(new ApiError(418, 'BRAND_NEW', 'Something new'))).toBe('Something new');
  });
});
