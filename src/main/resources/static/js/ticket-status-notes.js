// Keeps the resolution-notes field required only while resolving or closing a ticket.
(function () {
  var statusSelect = document.getElementById('status');
  var notes = document.getElementById('notes');
  if (!statusSelect || !notes) return;
  var setNotesRule = function () {
    notes.required = statusSelect.value === 'RESOLVED' || statusSelect.value === 'CLOSED';
  };
  statusSelect.addEventListener('change', setNotesRule);
  setNotesRule();
})();
