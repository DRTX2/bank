const BASE32 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';

/**
 * Published demo secrets (same idea as the published demo password, see DemoDataSeeder). They let a reviewer
 * try MFA without a phone; real users enroll their own secret under Seguridad and it never reaches the browser again.
 */
export const DEMO_MFA_SECRETS: Partial<Record<string, string>> = {
  'gerente@andina.ec': 'KUNTURDEMOGERENTEMFAANDINAKUNTUR',
};

function decode(base32: string): Uint8Array<ArrayBuffer> {
  const out: number[] = [];
  let buffer = 0;
  let bits = 0;
  for (const c of base32.replace(/[\s=]/g, '').toUpperCase()) {
    buffer = (buffer << 5) | BASE32.indexOf(c);
    bits += 5;
    if (bits >= 8) {
      out.push((buffer >> (bits - 8)) & 0xff);
      bits -= 8;
    }
  }
  return new Uint8Array(out);
}

/** RFC 6238 code (HMAC-SHA1, 6 digits, 30 s), the same algorithm as the server and authenticator apps. */
export async function totp(secret: string, atMillis = Date.now()): Promise<string> {
  const message = new ArrayBuffer(8);
  new DataView(message).setBigUint64(0, BigInt(Math.floor(atMillis / 30_000)));
  const key = await crypto.subtle.importKey('raw', decode(secret), { name: 'HMAC', hash: 'SHA-1' }, false, [
    'sign',
  ]);
  const h = new Uint8Array(await crypto.subtle.sign('HMAC', key, message));
  const at = (i: number) => h[i] ?? 0;
  const o = at(h.length - 1) & 0xf;
  const binary = ((at(o) & 0x7f) << 24) | (at(o + 1) << 16) | (at(o + 2) << 8) | at(o + 3);
  return String(binary % 1_000_000).padStart(6, '0');
}
