'use strict';
(async () => {
  const error = document.getElementById('login-error');
  if (new URLSearchParams(location.search).has('error')) { error.hidden = false; error.textContent = 'Sign-in failed. Check your username and password.'; }
  try {
    const response = await fetch('/session/csrf', {cache: 'no-store'});
    if (!response.ok) throw new Error();
    const csrf = await response.json();
    document.getElementById('csrf').name = csrf.parameterName;
    document.getElementById('csrf').value = csrf.token;
    document.getElementById('sign-in').disabled = false;
  } catch { error.hidden = false; error.textContent = 'Unable to initialize a secure session. Reload to try again.'; }
})();
