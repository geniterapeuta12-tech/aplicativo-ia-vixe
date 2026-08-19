const { app, BrowserWindow, dialog, ipcMain, shell } = require('electron');
const path = require('path');
const fs = require('fs');

const VERSION = '2.1.0-alpha.1';

function safeName(name) {
  let value = String(name || 'pagina.html').trim();
  if (!/\.html?$/i.test(value)) value += '.html';
  return value.replace(/[<>:"/\\|?*\x00-\x1F]/g, '_');
}

function dataFile(name) {
  const dir = path.join(app.getPath('userData'), 'CoderBuilder');
  fs.mkdirSync(dir, { recursive: true });
  return path.join(dir, name);
}

function readJson(file, fallback) {
  try {
    if (!fs.existsSync(file)) return fallback;
    return JSON.parse(fs.readFileSync(file, 'utf8'));
  } catch {
    return fallback;
  }
}

function writeJson(file, value) {
  fs.writeFileSync(file, JSON.stringify(value, null, 2), 'utf8');
}

function projectsFile() { return dataFile('projects.json'); }
function exportsFile() { return dataFile('exports.json'); }
function settingsFile() { return dataFile('settings.json'); }

function getProjects() {
  const list = readJson(projectsFile(), []);
  return Array.isArray(list) ? list : [];
}

function saveProjects(list) {
  writeJson(projectsFile(), list.slice(0, 100));
}

function getExports() {
  const list = readJson(exportsFile(), []);
  return Array.isArray(list) ? list : [];
}

function saveExports(list) {
  writeJson(exportsFile(), list.slice(0, 60));
}

function recordExport(filePath) {
  const list = getExports().filter(item => item.path !== filePath);
  list.unshift({
    name: path.basename(filePath),
    path: filePath,
    exportedAt: Date.now()
  });
  saveExports(list);
}

function getSettings() {
  const defaults = {
    exportDir: app.getPath('downloads')
  };
  return Object.assign(defaults, readJson(settingsFile(), {}));
}

function saveSettings(next) {
  const merged = Object.assign(getSettings(), next || {});
  writeJson(settingsFile(), merged);
  return merged;
}

function createWindow() {
  const win = new BrowserWindow({
    width: 1480,
    height: 920,
    minWidth: 980,
    minHeight: 680,
    backgroundColor: '#07111F',
    title: `CoderBuilder ${VERSION}`,
    autoHideMenuBar: true,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true
    }
  });

  win.loadFile('index.html');
}

ipcMain.handle('save-html', async (_event, payload) => {
  try {
    const name = safeName(payload?.name);
    const settings = getSettings();
    const result = await dialog.showSaveDialog({
      title: 'Salvar arquivo HTML',
      defaultPath: path.join(settings.exportDir || app.getPath('downloads'), name),
      filters: [
        { name: 'Arquivo HTML', extensions: ['html', 'htm'] },
        { name: 'Todos os arquivos', extensions: ['*'] }
      ]
    });
    if (result.canceled || !result.filePath) return { saved: false };
    fs.writeFileSync(result.filePath, String(payload?.html || ''), 'utf8');
    recordExport(result.filePath);
    saveSettings({ exportDir: path.dirname(result.filePath) });
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

ipcMain.handle('list-projects', async () => {
  return getProjects().map(({ id, name, updatedAt }) => ({ id, name, updatedAt }));
});

ipcMain.handle('get-project', async (_event, id) => {
  const item = getProjects().find(p => p.id === id);
  return item || null;
});

ipcMain.handle('save-project', async (_event, payload) => {
  const list = getProjects();
  const now = Date.now();
  const id = String(payload?.id || ('p_' + now + '_' + Math.random().toString(36).slice(2, 8)));
  const item = {
    id,
    name: safeName(payload?.name || 'pagina.html'),
    html: String(payload?.html || ''),
    updatedAt: now
  };
  const index = list.findIndex(p => p.id === id);
  if (index >= 0) list[index] = item; else list.unshift(item);
  list.sort((a, b) => b.updatedAt - a.updatedAt);
  saveProjects(list);
  return item;
});

ipcMain.handle('delete-project', async (_event, id) => {
  const before = getProjects();
  const after = before.filter(p => p.id !== id);
  saveProjects(after);
  return { deleted: after.length !== before.length };
});

ipcMain.handle('duplicate-project', async (_event, id) => {
  const list = getProjects();
  const source = list.find(p => p.id === id);
  if (!source) return null;
  const now = Date.now();
  const base = source.name.replace(/\.html?$/i, '');
  const copy = {
    id: 'p_' + now + '_' + Math.random().toString(36).slice(2, 8),
    name: safeName(base + '_copia.html'),
    html: source.html,
    updatedAt: now
  };
  list.unshift(copy);
  saveProjects(list);
  return copy;
});

ipcMain.handle('rename-project', async (_event, payload) => {
  const list = getProjects();
  const item = list.find(p => p.id === payload?.id);
  if (!item) return null;
  item.name = safeName(payload?.name);
  item.updatedAt = Date.now();
  saveProjects(list);
  return item;
});

ipcMain.handle('list-exports', async () => {
  return getExports().map(item => ({
    ...item,
    exists: fs.existsSync(item.path)
  }));
});

ipcMain.handle('open-export', async (_event, filePath) => {
  try {
    if (!filePath || !fs.existsSync(filePath)) return { opened: false, missing: true };
    const error = await shell.openPath(filePath);
    return { opened: !error, error: error || null };
  } catch (error) {
    return { opened: false, error: error.message };
  }
});

ipcMain.handle('reveal-export', async (_event, filePath) => {
  try {
    if (!filePath || !fs.existsSync(filePath)) return { shown: false };
    shell.showItemInFolder(filePath);
    return { shown: true };
  } catch {
    return { shown: false };
  }
});

ipcMain.handle('get-app-settings', async () => getSettings());

ipcMain.handle('choose-export-dir', async () => {
  const result = await dialog.showOpenDialog({
    title: 'Escolher pasta padrão de exportação',
    properties: ['openDirectory', 'createDirectory']
  });
  if (result.canceled || !result.filePaths?.[0]) return null;
  const settings = saveSettings({ exportDir: result.filePaths[0] });
  return settings.exportDir;
});

ipcMain.handle('get-version', async () => VERSION);

app.whenReady().then(() => {
  createWindow();
  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});
