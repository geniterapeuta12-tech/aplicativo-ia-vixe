const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('coderBuilder', {
  saveHtml: (name, html) => ipcRenderer.invoke('save-html', { name, html }),
  openHtml: (name, html) => ipcRenderer.invoke('open-html', { name, html }),
  listProjects: () => ipcRenderer.invoke('list-projects'),
  getProject: (id) => ipcRenderer.invoke('get-project', id),
  saveProject: (project) => ipcRenderer.invoke('save-project', project),
  deleteProject: (id) => ipcRenderer.invoke('delete-project', id),
  duplicateProject: (id) => ipcRenderer.invoke('duplicate-project', id),
  renameProject: (id, name) => ipcRenderer.invoke('rename-project', { id, name }),
  listExports: () => ipcRenderer.invoke('list-exports'),
  openExport: (filePath) => ipcRenderer.invoke('open-export', filePath),
  revealExport: (filePath) => ipcRenderer.invoke('reveal-export', filePath),
  getAppSettings: () => ipcRenderer.invoke('get-app-settings'),
  chooseExportDir: () => ipcRenderer.invoke('choose-export-dir'),
  getVersion: () => ipcRenderer.invoke('get-version')
});
