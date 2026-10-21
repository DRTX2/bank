import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { RiskMeter } from '../components/RiskMeter';
import { StatusPill } from '../components/StatusPill';
import { groupIban, money } from '../lib/format';

describe('RiskMeter', () => {
  it('explains the score signal by signal', () => {
    render(<RiskMeter score={65} signals={['AMOUNT_SPIKE', 'MULE_FAN_IN', 'NEW_BENEFICIARY']} />);
    expect(screen.getByRole('meter', { name: 'Puntaje de riesgo' })).toHaveAttribute('aria-valuenow', '65');
    expect(screen.getByText(/posible cuenta mula/)).toBeInTheDocument();
    expect(screen.getByText('+30')).toBeInTheDocument();
  });

  it('shows unknown codes verbatim instead of hiding them', () => {
    render(<RiskMeter score={12} signals={['SOMETHING_NEW']} />);
    expect(screen.getByText('SOMETHING_NEW')).toBeInTheDocument();
  });
});

describe('StatusPill', () => {
  it('pairs the state label with the ISO reason code', () => {
    render(<StatusPill status="FAILED" reason="AM04" />);
    expect(screen.getByText('Fallida')).toBeInTheDocument();
    expect(screen.getByText('AM04')).toBeInTheDocument();
  });
});

describe('format', () => {
  it('groups account numbers in fours', () => {
    expect(groupIban('EC12ANDN12345678901234')).toBe('EC12 ANDN 1234 5678 9012 34');
  });
  it('formats dollars for Ecuador', () => {
    expect(money(1234.5)).toMatch(/1\.234,50/);
  });
});
