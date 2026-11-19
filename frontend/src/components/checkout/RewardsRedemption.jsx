import { getRewardAccount } from '../../api/rewardApi.js';
import useAsync from '../../hooks/useAsync.js';
import { formatMoney, formatPoints } from '../../utils/money.js';
import Field from '../common/Field.jsx';
import './RewardsRedemption.css';

const WHOLE_NUMBER = /^\d+$/;

/**
 * Checks what the customer typed against the cap. An empty box means no redemption. Returns `{ points, error }`:
 * `points` is the whole number to redeem (0 when empty or not usable) and `error` explains why the text cannot be
 * used. The server repeats these checks when the order is placed.
 */
export function checkPoints(text, max) {
  const value = text.trim();
  if (value === '') return { points: 0, error: null };
  if (!WHOLE_NUMBER.test(value)) return { points: 0, error: 'Enter a whole number of points, for example 100.' };
  // More than nine digits can never be within the cap; it is not converted, so no precision is lost.
  if (value.length > 9 || Number(value) > max) {
    return {
      points: 0,
      error: `You can redeem at most ${formatPoints(max)} ${max === 1 ? 'point' : 'points'} on this order.`,
    };
  }
  return { points: Number(value), error: null };
}

/**
 * The redemption area below the order summary (SCR-07, FR-CHK-07, FR-RWD-01, 02, 03, 05): the available balance, the
 * maximum redeemable points (`maxRedeemablePoints` of the quote, already min(balance, floor(50% of the total))) and an
 * input for the points to redeem. The page owns the typed `value` and the `error` found by `checkPoints`, so the
 * summary and Pay Now follow the same text. A zero balance shows redemption as unavailable with the reason.
 */
export default function RewardsRedemption({ maxRedeemablePoints, value, error, onChange }) {
  const account = useAsync((signal) => getRewardAccount(signal), []);
  const balance = account.data?.balance;

  let body;
  if (account.error) {
    body = <p className="rewards__note">Your reward points could not be loaded.</p>;
  } else if (balance === undefined || balance === null) {
    body = <p className="rewards__note">Loading your reward points…</p>;
  } else if (balance === 0) {
    body = <p className="rewards__note">You have no reward points to redeem.</p>;
  } else {
    body = (
      <>
        <dl className="rewards__figures">
          <div>
            <dt>Available balance</dt>
            <dd>{formatPoints(balance)} points</dd>
          </div>
          <div>
            <dt>Maximum you can redeem</dt>
            <dd>
              {formatPoints(maxRedeemablePoints)} {maxRedeemablePoints === 1 ? 'point' : 'points'}
            </dd>
          </div>
        </dl>
        {maxRedeemablePoints === 0 ? (
          <p className="rewards__note">Reward points cannot be used on this order.</p>
        ) : (
          <Field
            label="Points to redeem"
            type="text"
            inputMode="numeric"
            autoComplete="off"
            value={value}
            onChange={(event) => onChange(event.target.value)}
            error={error}
            hint={`1 point = ${formatMoney('1.00')}. Leave empty to pay without points.`}
          />
        )}
      </>
    );
  }

  return (
    <section className="rewards" aria-label="Reward points">
      <h2 className="checkout__section-heading">Reward points</h2>
      {body}
    </section>
  );
}
