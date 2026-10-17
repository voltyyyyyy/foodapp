'use strict';
const parts = new URLSearchParams(location.hash.slice(1));
let secret = parts.get('verify') || parts.get('reset');
const purpose = parts.has('verify') ? 'verify' : parts.has('reset') ? 'reset' : 'request';
history.replaceState(null, '', location.pathname);
const title = document.querySelector('#title'), message = document.querySelector('#message'), host = document.querySelector('#form');
async function request(path, body) {
  const response = await fetch('/api/auth/' + path, {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(body), cache:'no-store', credentials:'omit'});
  const result = await response.json();
  if (!response.ok) throw new Error(Array.isArray(result.detail) ? result.detail.map(x=>x.msg).join(' ') : result.detail || 'Please try again.');
  return result;
}
function recovery() {
  secret = null;
  title.textContent = 'Request a new account link';
  host.innerHTML = '<form class="panel"><label>Email address<input name="email" type="email" autocomplete="email" required maxlength="254"></label><label>Link type<select name="purpose"><option value="forgot-password">Reset password</option><option value="resend-verification">Verify email</option></select></label><button class="primary">Send link</button></form>';
  host.querySelector('form').onsubmit = async event => {
    event.preventDefault(); const button = event.target.querySelector('button'); button.disabled = true;
    const values = new FormData(event.target);
    try { message.textContent = (await request(values.get('purpose'), {email:values.get('email')})).message; }
    catch (error) { message.textContent = error.message; }
    finally { button.disabled = false; }
  };
}
if (purpose === 'verify' || purpose === 'reset') {
  title.textContent = purpose === 'verify' ? 'Verify your email' : 'Choose a new password';
  host.innerHTML = '<form class="panel">' + (purpose === 'reset' ? '<label>New password<input name="password" type="password" autocomplete="new-password" required minlength="12" maxlength="128"></label><label>Confirm password<input name="confirmation" type="password" autocomplete="new-password" required minlength="12" maxlength="128"></label><p>Use 12 to 128 characters. All existing FoodBridge sessions will end.</p>' : '<p>Confirm to activate your FoodBridge account.</p>') + '<button class="primary">' + (purpose === 'verify' ? 'Verify email' : 'Reset password') + '</button></form><button id="replacement">Request a new link</button>';
  document.querySelector('#replacement').onclick = recovery;
  host.querySelector('form').onsubmit = async event => {
    event.preventDefault(); const button = event.target.querySelector('button'); button.disabled = true;
    const values = Object.fromEntries(new FormData(event.target));
    event.target.querySelectorAll('input[type=password]').forEach(input=>input.value='');
    try {
      message.textContent = (await request(purpose === 'verify' ? 'verify' : 'reset-password', {...values, token:secret})).message;
      secret = null; host.innerHTML = '<p>You can close this page and return to FoodBridge to sign in.</p>';
    } catch (error) { message.textContent = error.message; button.disabled = false; }
  };
} else recovery();
