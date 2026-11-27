// Disables employee assignment while an asset is marked RETIRED.
(function () {
  var statusSel = document.getElementById('assetStatusSelect');
  var userSel = document.getElementById('userSelect');
  var warning = document.getElementById('retiredWarning');

  function handleStatusChange() {
    if (!statusSel || !userSel) return;
    if (statusSel.value === 'RETIRED') {
      userSel.value = '';
      userSel.disabled = true;
      if (warning) warning.style.display = 'block';
    } else {
      userSel.disabled = false;
      if (warning) warning.style.display = 'none';
    }
  }

  if (statusSel) {
    statusSel.addEventListener('change', handleStatusChange);
    handleStatusChange();
  }
})();
