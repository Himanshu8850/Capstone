const METHODS = {
  CREDIT_CARD: 'Credit Card',
  DEBIT_CARD: 'Debit Card',
  UPI: 'UPI',
  COD: 'Cash on Delivery',
};

const ORDER_STATUSES = {
  PLACED: 'Placed',
  CANCELLED: 'Cancelled',
};

const PAYMENT_STATUSES = {
  PAID: 'Paid',
  PENDING_ON_DELIVERY: 'Pending (pay on delivery)',
  REFUNDED: 'Refunded',
  CANCELLED: 'Cancelled',
};

/** Customer-facing name of a contract `PaymentMethod`; an unknown value is shown as given. */
export function paymentMethodLabel(method) {
  return METHODS[method] ?? String(method ?? '');
}

/** Customer-facing name of a contract `PaymentStatus` (FR-PAY-12); an unknown value is shown as given. */
export function paymentStatusLabel(status) {
  return PAYMENT_STATUSES[status] ?? String(status ?? '');
}

/** Customer-facing name of a contract `OrderStatus` (an order is only ever Placed or Cancelled, CL-24). */
export function orderStatusLabel(status) {
  return ORDER_STATUSES[status] ?? String(status ?? '');
}
