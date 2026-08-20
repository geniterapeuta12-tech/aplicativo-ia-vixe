const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('coderBuilder', {
  saveHtml: (name, html) => ipcRenderer.invoke('save-html', { name, html }),
  openHtml: (name, html) => ipcRenderer.invoke('open-html', { name, html })
});
