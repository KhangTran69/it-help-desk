// Filter selects resubmit their form; unassign forms confirm with the current assignee.
(function () {
  document.querySelectorAll('select[data-auto-submit]').forEach(function (select) {
    select.addEventListener('change', function () {
      select.form.submit();
    });
  });
  document.querySelectorAll('form[data-confirm-unassign]').forEach(function (form) {
    form.addEventListener('submit', function (event) {
      var assignee = form.getAttribute('data-assigned') || 'this user';
      if (!window.confirm('Unassign this asset from ' + assignee + '?')) {
        event.preventDefault();
      }
    });
  });
})();
