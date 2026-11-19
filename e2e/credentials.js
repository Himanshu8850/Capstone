// Passwords used by E2E tests. Nothing is committed: values come from TEST_USER_PASSWORD and
// TEST_USER_WRONG_PASSWORD in the git-ignored .env in the repository root (see .env.example) or the environment.
function required(name) {
  const value = process.env[name];
  if (!value || value === 'CHANGE_ME') {
    throw new Error(`${name} is not set. Copy .env.example to .env and set it, or export it as an environment variable.`);
  }
  return value;
}

export const PASSWORD = required('TEST_USER_PASSWORD');
export const WRONG_PASSWORD = required('TEST_USER_WRONG_PASSWORD');
