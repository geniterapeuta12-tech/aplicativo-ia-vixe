const { app, BrowserWindow, dialog, ipcMain, shell } = require('electron');
const path = require('path');
const fs = require('fs');

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
    title: 'CoderBuilder 2.0',
    autoHideMenuBar: true,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true
    }
  });

  win.loadFile('index.html');

  win.webContents.on('did-finish-load', () => {
    win.webContents.executeJavaScript(`
      (() => {
        const fileName = document.getElementById('fileName');
        const code = document.getElementById('code');
        const state = document.getElementById('state');
        const normalize = () => {
          let n=(fileName?.value||'pagina.html').trim()||'pagina.html';
          if(!/\\.html?$/i.test(n)) n+='.html';
          return n;
        };
        const saveDesktop = async () => {
          if (!window.coderBuilder) return;
          const result = await window.coderBuilder.saveHtml(normalize(), code?.value || '');
          if (state) state.textContent = result?.saved ? 'HTML salvo' : 'Salvamento cancelado';
        };
        const openDesktop = async () => {
          if (!window.coderBuilder) return;
          const result = await window.coderBuilder.openHtml(normalize(), code?.value || '');
          if (state) state.textContent = result?.opened ? 'Aberto no navegador' : 'Não foi possível abrir';
        };
        const save = document.getElementById('save');
        const open = document.getElementById('open');
        const open2 = document.getElementById('open2');
        if (save) save.onclick = saveDesktop;
        if (open) open.onclick = openDesktop;
        if (open2) open2.onclick = openDesktop;
      })();
    `).catch(() => {});
  });
}

ipcMain.handle('save-html', async (_event, payload) => {
  try {
    const name = safeName(payload?.name);
    const result = await dialog.showSaveDialog({
      title: 'Salvar arquivo HTML',
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
    dialog.showErrorBox('CoderBuilder', 'Não foi possível salvar o HTML.\n\n' + error.message);
    return { saved: false, error: error.message };
  }
});

ipcMain.handle('open-html', async (_event, payload) => {
  try {
    const name = safeName(payload?.name);
    const folder = path.join(app.getPath('temp'), 'CoderBuilder');
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
