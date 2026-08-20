const { app, BrowserWindow, dialog, ipcMain, shell } = require('electron');
const path = require('path');
const fs = require('fs');

const CLOUD_SCRIPT = 'https://whtjqfdhhtahcuazzgee.supabase.co/functions/v1/coderbuilder-cloud-client';

function safeName(name) {
  let value = String(name || 'pagina.html').trim();
  if (!/\.html?$/i.test(value)) value += '.html';
  return value.replace(/[<>:"/\\|?*\x00-\x1F]/g, '_');
}

function createWindow() {
  const win = new BrowserWindow({
    width: 1440,
    height: 900,
    minWidth: 960,
    minHeight: 650,
    backgroundColor: '#07111F',
    title: 'CoderBuilder 2.5',
    autoHideMenuBar: true,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true
    }
  });

  win.webContents.on('did-finish-load', () => {
    const src = JSON.stringify(CLOUD_SCRIPT);
    win.webContents.executeJavaScript(`(function(){if(document.getElementById('cb-cloud-v25'))return;var s=document.createElement('script');s.id='cb-cloud-v25';s.src=${src};s.async=true;document.body.appendChild(s);})();`).catch(() => {});
  });

  win.loadFile('index.html');
}

ipcMain.handle('save-html', async (_event, payload) => {
  try {
    const name = safeName(payload?.name);
    const result = await dialog.showSaveDialog({
      title: 'Exportar HTML',
      defaultPath: path.join(app.getPath('documents'), name),
      filters: [
        { name: 'Arquivo HTML', extensions: ['html', 'htm'] },
        { name: 'Todos os arquivos', extensions: ['*'] }
      ]
    });
    if (result.canceled || !result.filePath) return { saved: false };
    fs.writeFileSync(result.filePath, String(payload?.html || ''), 'utf8');
    return { saved: true, path: result.filePath };
  } catch (error) {
    dialog.showErrorBox('CoderBuilder', 'Não foi possível exportar o HTML.\n\n' + error.message);
    return { saved: false, error: error.message };
  }
});

ipcMain.handle('open-html', async (_event, payload) => {
  try {
    const name = safeName(payload?.name);
    const folder = path.join(app.getPath('temp'), 'CoderBuilder-2.5');
    fs.mkdirSync(folder, { recursive: true });
    const file = path.join(folder, name);
    fs.writeFileSync(file, String(payload?.html || ''), 'utf8');
    const result = await shell.openPath(file);
    if (result) throw new Error(result);
    return { opened: true, path: file };
  } catch (error) {
    dialog.showErrorBox('CoderBuilder', 'Não foi possível abrir o HTML no navegador.\n\n' + error.message);
    return { opened: false, error: error.message };
  }
});

app.whenReady().then(() => {
  createWindow();
  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});
