import qrcode from 'qrcode-generator';

/** Renders `value` as an SVG QR code. Pure SVG attributes, so the strict CSP (no inline styles) is untouched. */
export function QrCode({ value, label }: { value: string; label: string }) {
  const qr = qrcode(0, 'M');
  qr.addData(value);
  qr.make();
  const n = qr.getModuleCount();
  let path = '';
  for (let r = 0; r < n; r++) {
    for (let c = 0; c < n; c++) {
      if (qr.isDark(r, c)) path += `M${String(c)} ${String(r)}h1v1h-1z`;
    }
  }
  // 4-module quiet zone, as the spec requires; the white background keeps it scannable in dark mode.
  const size = n + 8;
  return (
    <svg
      role="img"
      aria-label={label}
      viewBox={`-4 -4 ${String(size)} ${String(size)}`}
      className="size-44 rounded-md border border-line bg-white"
      shapeRendering="crispEdges"
    >
      <path d={path} fill="#000" />
    </svg>
  );
}
