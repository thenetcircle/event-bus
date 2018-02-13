JSONEditor.defaults.themes.eventbus = JSONEditor.AbstractTheme.extend({
  getFormControl: function(label, input, description) {
    var el = document.createElement('div');
    el.className = 'field';
    if (label) {
      label.className = 'label';
      el.appendChild(label);
    }
    if (input.type == 'checkbox') {
      label.insertBefore(input, label.firstChild);
    }
    else {
      var inputBlock = document.createElement('div');
      inputBlock.className = 'control';
      inputBlock.appendChild(input);
      el.appendChild(inputBlock);
    }

    if (description) {
      var descBlock = document.createElement('p');
      descBlock.className = 'help';
      descBlock.appendChild(description);
      el.appendChild(descBlock);
    }
    return el;
  },
  getSelectInput: function(options) {
    var el = this._super(options);
    /*
    var block = document.createElement('div');
    block.className = 'select';
    block.appendChild(select);
    return block;
     */
    return el;
  },
  getFormInputField: function(type) {
    var el = this._super(type);
    if(type !== 'checkbox') {
      el.className = 'input';
    }
    return el;
  },
  getIndentedPanel: function() {
    var el = this._super();
    el.style.border = '1px solid #ddd';
    el.style.padding = '5px';
    el.style.margin = '5px';
    el.style.borderRadius = '3px';
    return el;
  },
  getChildEditorHolder: function() {
    var el = this._super();
    el.style.marginBottom = '8px';
    return el;
  },
  getHeaderButtonHolder: function() {
    var el = this.getButtonHolder();
    el.style.display = 'inline-block';
    el.style.marginLeft = '10px';
    el.style.fontSize = '.8em';
    el.style.verticalAlign = 'middle';
    return el;
  },
  getButton: function(text, icon, title) {
    var el = this._super(text, icon, title);
    el.className += ' button is-small is-light';
    return el;
  },
  getTable: function() {
    var el = this._super();
    el.style.borderBottom = '1px solid #ccc';
    el.style.marginBottom = '5px';
    return el;
  },
  addInputError: function(input, text) {
    input.style.borderColor = 'red';

    if (!input.errmsg) {
      var group = this.closest(input, '.form-control');
      input.errmsg = document.createElement('div');
      input.errmsg.setAttribute('class', 'errmsg');
      input.errmsg.style = input.errmsg.style || {};
      input.errmsg.style.color = 'red';
      group.appendChild(input.errmsg);
    }
    else {
      input.errmsg.style.display = 'block';
    }

    input.errmsg.innerHTML = '';
    input.errmsg.appendChild(document.createTextNode(text));
  },
  removeInputError: function(input) {
    input.style.borderColor = '';
    if (input.errmsg) input.errmsg.style.display = 'none';
  },
  getProgressBar: function() {
    var max = 100, start = 0;

    var progressBar = document.createElement('progress');
    progressBar.setAttribute('max', max);
    progressBar.setAttribute('value', start);
    return progressBar;
  },
  updateProgressBar: function(progressBar, progress) {
    if (!progressBar) return;
    progressBar.setAttribute('value', progress);
  },
  updateProgressBarUnknown: function(progressBar) {
    if (!progressBar) return;
    progressBar.removeAttribute('value');
  },
});
