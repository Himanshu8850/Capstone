// Fixture data for frontend tests and for pages built before their backend operations exist.
// Every shape mirrors an example in api/openapi.yaml (the approved contract); money is a decimal string.

export const categories = [
  {
    id: 1,
    name: 'Fiction',
    children: [
      { id: 3, name: 'Romance', children: [] },
      { id: 4, name: 'Mystery', children: [] },
    ],
  },
  {
    id: 2,
    name: 'Non-Fiction',
    children: [{ id: 13, name: 'Self Help', children: [] }],
  },
];

export const brands = [
  { name: 'Maple Leaf Press', bookCount: 8 },
  { name: 'Northwind Books', bookCount: 6 },
];

const selfHelp = [{ id: 13, name: 'Self Help' }];

export const paperback = {
  id: 7,
  title: 'The Habit Loop',
  author: 'Meera Iyer',
  description: 'A practical guide to building small daily routines.',
  format: 'PAPERBACK',
  publisher: 'Maple Leaf Press',
  language: 'English',
  price: '500.00',
  frontCoverUrl: '/covers/7-front.svg',
  categories: selfHelp,
  available: true,
  stockQuantity: 25,
  deliveryDate: '2026-11-17',
};

export const ebook = {
  id: 12,
  title: 'Quiet Mornings',
  author: 'Meera Iyer',
  description: 'Essays on slowing down.',
  format: 'EBOOK',
  publisher: 'Northwind Books',
  language: 'English',
  price: '299.00',
  frontCoverUrl: '/covers/12-front.svg',
  categories: selfHelp,
  available: true,
  stockQuantity: null,
  deliveryDate: '2026-11-12',
};

export const outOfStock = {
  ...paperback,
  id: 31,
  title: 'Out of Print Atlas',
  available: false,
  stockQuantity: 0,
};

export const bookDetail = {
  ...paperback,
  backCoverUrl: '/covers/7-back.svg',
  breadcrumb: [
    { id: 2, name: 'Non-Fiction' },
    { id: 13, name: 'Self Help' },
  ],
};

export const ebookDetail = {
  ...ebook,
  backCoverUrl: null,
  breadcrumb: [
    { id: 2, name: 'Non-Fiction' },
    { id: 13, name: 'Self Help' },
  ],
};

export const outOfStockDetail = {
  ...bookDetail,
  id: 31,
  title: 'Out of Print Atlas',
  available: false,
  stockQuantity: 0,
};

export const bookPage = {
  items: [paperback, ebook],
  page: 0,
  pageSize: 12,
  totalItems: 14,
  totalPages: 2,
};

export const emptyBookPage = { items: [], page: 0, pageSize: 12, totalItems: 0, totalPages: 0 };

export const filterOptions = {
  languages: ['English', 'French', 'Hindi', 'Spanish'],
  formats: ['PAPERBACK', 'HARDCOVER', 'EBOOK'],
  priceBands: [
    { label: 'Under ₹200', minPrice: null, maxPrice: '199.99' },
    { label: '₹200–₹499', minPrice: '200.00', maxPrice: '499.99' },
    { label: '₹500–₹999', minPrice: '500.00', maxPrice: '999.99' },
    { label: '₹1,000 and above', minPrice: '1000.00', maxPrice: null },
  ],
};

export const customer = { id: 1, fullName: 'Asha Verma', email: 'asha.verma@example.com' };

export const address = {
  id: 1,
  fullName: 'Asha Verma',
  phone: '9876543210',
  addressLine1: '12 Lake View Road',
  addressLine2: 'Near City Park',
  city: 'Bengaluru',
  state: 'Karnataka',
  pinCode: '560001',
  country: 'India',
};

export const emptyBasket = {
  items: [],
  itemCount: 0,
  subtotal: '0.00',
  tax: '0.00',
  deliveryCharge: '0.00',
  total: '0.00',
};

export const basket = {
  items: [{ book: paperback, quantity: 2, lineTotal: '1000.00' }],
  itemCount: 2,
  subtotal: '1000.00',
  tax: '50.00',
  deliveryCharge: '40.00',
  total: '1090.00',
};

export const checkoutQuote = {
  subtotal: '1000.00',
  tax: '50.00',
  deliveryCharge: '40.00',
  total: '1090.00',
  balance: 1000,
  maxRedeemablePoints: 545,
  pointsToRedeem: 100,
  payableTotal: '990.00',
  issues: [],
};

export const order = {
  id: 2,
  reference: 'BG-000002',
  status: 'PLACED',
  placedAt: '2026-11-12T10:15:30Z',
  items: [{ book: { ...paperback, stockQuantity: 23 }, quantity: 2, unitPrice: '500.00', lineTotal: '1000.00' }],
  address: { ...address, id: undefined },
  paymentMethod: 'CREDIT_CARD',
  paymentStatus: 'PAID',
  subtotal: '1000.00',
  tax: '50.00',
  deliveryCharge: '40.00',
  pointsRedeemed: 100,
  payableTotal: '990.00',
  cancellable: true,
  cancellableUntil: '2026-11-14T10:15:30Z',
};

export const cancelledOrder = {
  ...order,
  status: 'CANCELLED',
  paymentStatus: 'REFUNDED',
  cancellable: false,
  cancellableUntil: null,
};

export const orderSummary = {
  id: order.id,
  reference: order.reference,
  status: order.status,
  placedAt: order.placedAt,
  items: order.items,
  payableTotal: order.payableTotal,
};

const timestamp = '2026-11-12T10:15:30Z';

/** One ApiError per contract error code. */
export const apiErrors = {
  VALIDATION_FAILED: {
    status: 400,
    code: 'VALIDATION_FAILED',
    message: 'The request contains invalid values.',
    fieldErrors: [{ field: 'phone', message: 'Phone must be exactly 10 digits.' }],
    timestamp,
  },
  UNAUTHENTICATED: { status: 401, code: 'UNAUTHENTICATED', message: 'Please log in to continue.', timestamp },
  INVALID_CREDENTIALS: {
    status: 401,
    code: 'INVALID_CREDENTIALS',
    message: 'The e-mail or password is incorrect.',
    timestamp,
  },
  EMAIL_TAKEN: {
    status: 409,
    code: 'EMAIL_TAKEN',
    message: 'An account with this e-mail address already exists.',
    timestamp,
  },
  NOT_FOUND: { status: 404, code: 'NOT_FOUND', message: 'The requested item was not found.', timestamp },
  BOOK_NOT_FOUND: { status: 404, code: 'BOOK_NOT_FOUND', message: 'This book was not found.', timestamp },
  QUANTITY_LIMIT: {
    status: 409,
    code: 'QUANTITY_LIMIT',
    message: 'You can add at most 10 copies of one book.',
    timestamp,
  },
  OUT_OF_STOCK: { status: 409, code: 'OUT_OF_STOCK', message: 'Only 1 copy of this book is available.', timestamp },
  EMPTY_BASKET: { status: 409, code: 'EMPTY_BASKET', message: 'Your basket is empty.', timestamp },
  BASKET_CHANGED: {
    status: 409,
    code: 'BASKET_CHANGED',
    message: 'Prices or availability in your basket have changed. Please review your basket.',
    timestamp,
  },
  POINTS_LIMIT_EXCEEDED: {
    status: 409,
    code: 'POINTS_LIMIT_EXCEEDED',
    message: 'You can redeem at most 545 points on this order.',
    timestamp,
  },
  PAYMENT_DECLINED: {
    status: 409,
    code: 'PAYMENT_DECLINED',
    message: 'Your payment was declined. Please try again or choose another payment method.',
    timestamp,
  },
  CANCELLATION_WINDOW_EXPIRED: {
    status: 409,
    code: 'CANCELLATION_WINDOW_EXPIRED',
    message: 'This order can no longer be cancelled because 48 hours have passed.',
    timestamp,
  },
  ALREADY_CANCELLED: {
    status: 409,
    code: 'ALREADY_CANCELLED',
    message: 'This order has already been cancelled.',
    timestamp,
  },
  INTERNAL_ERROR: {
    status: 500,
    code: 'INTERNAL_ERROR',
    message: 'Something went wrong. Please try again later.',
    timestamp,
  },
};
