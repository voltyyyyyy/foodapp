'use strict';
const policy = document.querySelector('#policy');
function paragraph(text) { const p = document.createElement('p'); p.textContent = text; policy.append(p); }
fetch('/api/privacy', {cache:'no-store', credentials:'omit'}).then(async response => {
  if (!response.ok) throw new Error();
  const data = await response.json(); policy.replaceChildren();
  if (data.test_placeholder) paragraph('TEST PLACEHOLDER — development only. The contact and policy below are examples, not real operator details. Do not send personal data.');
  paragraph(data.retention_summary || 'The operator has not supplied retention information for this installation.');
  paragraph(data.request_guidance);
  if (data.contact_email) {
    const a = document.createElement('a'); a.href = 'mailto:' + encodeURIComponent(data.contact_email) + '?subject=' + encodeURIComponent('FoodBridge account/data request');
    a.textContent = (data.test_placeholder ? 'TEST email example: ' : 'Contact operator: ') + data.contact_email; policy.append(a);
    paragraph(data.test_placeholder ? 'This example mailbox is not a FoodBridge request route. Use it only to inspect the test interface.' : 'Your email app opens a draft. Sending it asks the operator for help; it does not automatically delete your account.');
  } else paragraph('An operator contact is unavailable for this installation. Account/data requests cannot be sent here yet.');
  if (data.policy_url) { const a = document.createElement('a'); a.href = data.policy_url; a.textContent = data.test_placeholder ? 'TEST policy link example (no retention policy established)' : 'Read the operator privacy policy'; a.rel = 'noopener noreferrer'; policy.append(a); }
}).catch(() => { policy.replaceChildren(); paragraph('Could not load operator information. Reload this page when connected.'); });
