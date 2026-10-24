'use strict';
const parts = new URLSearchParams(location.hash.slice(1));
let secret = parts.get('verify') || parts.get('reset');
const purpose = parts.has('verify') ? 'verify' : parts.has('reset') ? 'reset' : 'request';
history.replaceState(null, '', location.pathname);
const title = document.querySelector('#title'), message = document.querySelector('#message'), host = document.querySelector('#form');
async function request(path, body) {
  let response;
  try { response = await fetch('/api/auth/' + path, {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(body), cache:'no-store', credentials:'omit'}); }
  catch (_) { throw new Error('Could not connect. Your input is still here; try again.'); }
  const result = await response.json();
  if (!response.ok) {
    const error = new Error(Array.isArray(result.detail) ? 'Check the highlighted fields and try again.' : result.detail || 'Please try again.');
    error.fields = Array.isArray(result.detail) ? result.detail : [];
    throw error;
  }
  return result;
}
function clearErrors(form) {
  form.querySelectorAll('[data-error]').forEach(node => node.remove());
  form.querySelectorAll('input').forEach(input => { input.removeAttribute('aria-invalid'); input.removeAttribute('aria-describedby'); });
}
function showError(error, form) {
  message.textContent = error.message;
  for (const issue of error.fields || []) {
    const field = issue.loc[issue.loc.length - 1];
    const input = Array.from(form.elements).find(element => element.name === field);
    if (!input) continue;
    const note = document.createElement('span'); note.dataset.error = 'true'; note.id = 'error-' + field;
    note.textContent = issue.msg.replace(/^Value error, /, '');
    input.setAttribute('aria-invalid', 'true'); input.setAttribute('aria-describedby', note.id); input.after(note);
  }
  const first = form.querySelector('[aria-invalid="true"]');
  if (first) { first.focus(); first.scrollIntoView({block:'center', behavior:'smooth'}); }
}
function busy(form, enabled) { Array.from(form.elements).forEach(element => { element.disabled = enabled; }); }
function recovery() {
  secret = null;
  title.textContent = 'Request a new account link';
  host.innerHTML = '<form class="panel"><label>Email address<input name="email" type="email" autocomplete="email" required maxlength="254"></label><label>Link type<select name="purpose"><option value="forgot-password">Reset password</option><option value="resend-verification">Verify email</option></select></label><button class="primary">Send link</button></form>';
  host.querySelector('form').onsubmit = async event => {
    event.preventDefault(); const form = event.target; clearErrors(form);
    const values = new FormData(form); busy(form, true);
    try { message.textContent = (await request(values.get('purpose'), {email:values.get('email')})).message; }
    catch (error) { busy(form, false); showError(error, form); }
    finally { busy(form, false); }
  };
}
if (purpose === 'verify' || purpose === 'reset') {
  title.textContent = purpose === 'verify' ? 'Verify your email' : 'Choose a new password';
  host.innerHTML = '<form class="panel">' + (purpose === 'reset' ? '<label>New password<input name="password" type="password" autocomplete="new-password" required minlength="12" maxlength="128"></label><label>Confirm password<input name="confirmation" type="password" autocomplete="new-password" required minlength="12" maxlength="128"></label><p>Use 12 to 128 characters. All existing FoodBridge sessions will end.</p>' : '<p>Confirm to activate your FoodBridge account.</p>') + '<button class="primary">' + (purpose === 'verify' ? 'Verify email' : 'Reset password') + '</button></form><button id="replacement">Request a new link</button>';
  document.querySelector('#replacement').onclick = recovery;
  host.querySelector('form').onsubmit = async event => {
    event.preventDefault(); const form = event.target; clearErrors(form);
    const values = Object.fromEntries(new FormData(form)); busy(form, true);
    document.querySelector('#replacement').disabled = true;
    try {
      message.textContent = (await request(purpose === 'verify' ? 'verify' : 'reset-password', {...values, token:secret})).message;
      form.querySelectorAll('input[type=password]').forEach(input => { input.value = ''; });
      secret = null;
      host.innerHTML = '<p>Finished. Open FoodBridge and sign in with your account.</p><a class="primary" href="foodbridge://account/return">Return to FoodBridge</a><p>If the app does not open, launch FoodBridge from your phone’s app list. On a computer, continue on your Android device.</p>';
    } catch (error) {
      busy(form, false); showError(error, form); document.querySelector('#replacement').disabled = false;
    }
  };
} else recovery();
