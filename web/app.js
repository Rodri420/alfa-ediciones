(function () {
  // 1) Configurar Firebase Web
  // Reemplaza el siguiente objeto con tu configuración de Firebase (Firebase Console -> Project settings -> Your apps -> Web app)
  const firebaseConfig = {
    apiKey: "AIzaSyA-S07R9O403sMdr6NpNAA-EorCfhm5NfI",
    authDomain: "alfa-206d1.firebaseapp.com",
    databaseURL: "https://alfa-206d1-default-rtdb.firebaseio.com",
    projectId: "alfa-206d1",
    storageBucket: "alfa-206d1.firebasestorage.app",
    messagingSenderId: "601425549469",
    appId: "1:601425549469:web:18d1345fcf06d01b88dc74",
    measurementId: "G-DMKQDL9ERR"
  };

  try {
    firebase.initializeApp(firebaseConfig);
  } catch (e) {
    console.warn("Firebase init warning:", e?.message || e);
  }

  const db = (firebase && firebase.firestore) ? firebase.firestore() : null;

  // 2) Navegación básica
  const screens = Array.from(document.querySelectorAll('.screen'));
  function show(id) {
    screens.forEach(s => s.classList.remove('active'));
    const el = document.getElementById(id);
    if (el) el.classList.add('active');
  }
  document.querySelectorAll('[data-go]')?.forEach(btn => btn.addEventListener('click', () => show(`screen-${btn.getAttribute('data-go')}`)));
  document.querySelector('[button][data-nav]');
  document.querySelectorAll('[data-nav]')?.forEach(btn => btn.addEventListener('click', () => {
    const dest = btn.getAttribute('data-nav');
    if (dest === 'home') show('screen-home');
    if (dest === 'menu') show('screen-menu');
    if (dest === 'admin') show('screen-admin-login');
  }));
  show('screen-home');

  const dniCuil = document.getElementById('dniCuil');
  const genero = document.getElementById('genero');
  const calcularBtn = document.getElementById('calcularBtn');
  const resultadoCuil = document.getElementById('resultadoCuil');
  const lockBanner = document.getElementById('lockBanner');

  // 3) Utilidades
  function sanitizeDni(dni) {
    if (!dni) return '';
    return (dni + '').replace(/\D/g, '').slice(0, 8);
  }

  function calculateCuil(dni, gender) {
    dni = sanitizeDni(dni);
    if (dni.length < 7) return 'El DNI debe tener 7 u 8 dígitos.';
    const paddedDni = dni.padStart(8, '0');
    const prefix = gender === 'Masculino' ? '20' : '27';
    const base = prefix + paddedDni;
    const weights = [5, 4, 3, 2, 7, 6, 5, 4, 3, 2];
    const sum = base.split('').reduce((acc, char, index) => acc + Number(char) * weights[index], 0);
    const remainder = sum % 11;
    const verifier = remainder === 0 ? 0 : (remainder === 1 ? (gender === 'Masculino' ? 9 : 4) : 11 - remainder);
    const finalPrefix = remainder === 1 ? '23' : prefix;
    return `${finalPrefix}-${paddedDni}-${verifier}`;
  }

  // 4) Eventos

  if (calcularBtn) {
    calcularBtn.addEventListener('click', () => {
      resultadoCuil.textContent = calculateCuil(dniCuil.value, genero.value);
    });
  }

  // Estado de venta (consulta Debito/tarjeta/mercadopago)
  const dniVenta = document.getElementById('dniVenta');
  const buscarVentaBtn = document.getElementById('buscarVentaBtn');
  const resultadoVenta = document.getElementById('resultadoVenta');
  if (buscarVentaBtn) {
    buscarVentaBtn.addEventListener('click', async () => {
      const dni = sanitizeDni(dniVenta.value);
      if (!dni) { resultadoVenta.textContent = 'Ingrese DNI válido'; return; }
      if (!db) { resultadoVenta.textContent = 'Firestore no está configurado.'; return; }
      resultadoVenta.textContent = 'Buscando...';
      try {
        const cols = ['Debito', 'tarjeta', 'mercadopago'];
        const results = [];
        for (const col of cols) {
          const snap = await db.collection(col).where('dni', '==', dni).get();
          snap.forEach(d => results.push({ col, data: d.data() }));
        }
        if (results.length === 0) {
          resultadoVenta.textContent = 'No se encontraron ventas para ese DNI.';
        } else {
          resultadoVenta.innerHTML = results.map(r => `${r.col}: ${r.data.vendedorNombre || ''} - ${r.data.importeTotal || ''} - ${r.data.valorCuota || ''}`).join('<br/>');
        }
      } catch (e) {
        resultadoVenta.textContent = 'Error: ' + (e?.message || e);
      }
    });
  }

  // Cargar datos clientes (ventas)
  const metodoPago = document.getElementById('metodoPago');
  const pagoCBU = document.getElementById('pagoCBU');
  const pagoTarjeta = document.getElementById('pagoTarjeta');
  const pagoMP = document.getElementById('pagoMP');
  metodoPago?.addEventListener('change', () => {
    const m = metodoPago.value;
    pagoCBU.hidden = m !== 'CBU';
    pagoTarjeta.hidden = m !== 'Tarjeta';
    pagoMP.hidden = m !== 'MercadoPago';
  });
  const estadoCarga = document.getElementById('estadoCarga');
  document.getElementById('guardarCargaBtn')?.addEventListener('click', async () => {
    try {
      if (!db) { estadoCarga.textContent = 'Firestore no está configurado.'; return; }
      const dni = sanitizeDni(document.getElementById('dniCarga').value);
      const nombre = document.getElementById('nombreCarga').value.trim();
      const vendedorNombre = document.getElementById('vendedorNombre').value.trim();
      const importeTotal = Number(document.getElementById('importeTotal').value || 0);
      const valorCuota = Number(document.getElementById('valorCuota').value || 0);
      const metodo = metodoPago.value;
      if (!dni || !nombre || !vendedorNombre) { estadoCarga.textContent = 'Complete DNI, Nombre y Vendedor'; return; }
      const comunes = { dni, nombre, vendedorNombre, importeTotal, valorCuota, fechaCreacion: Date.now() };
      if (metodo === 'CBU') {
        const cbu = document.getElementById('cbu').value.trim();
        await db.collection('Debito').add({ ...comunes, cbu });
      } else if (metodo === 'Tarjeta') {
        const numeroTarjeta = document.getElementById('numeroTarjeta').value.trim();
        const fechaVencimiento = document.getElementById('fechaVencimiento').value.trim();
        const codigoTarjeta = document.getElementById('codigoTarjeta').value.trim();
        await db.collection('tarjeta').add({ ...comunes, numeroTarjeta, fechaVencimiento, codigoTarjeta });
      } else {
        const fechaVenta = document.getElementById('fechaVenta').value;
        await db.collection('mercadopago').add({ ...comunes, fechaVenta });
      }
      estadoCarga.textContent = 'Guardado con éxito';
    } catch (e) {
      estadoCarga.textContent = 'Error al guardar: ' + (e?.message || e);
    }
  });

  // PDF ejemplo
  document.getElementById('crearPdfBtn')?.addEventListener('click', () => {
    try {
      const { jsPDF } = window.jspdf;
      const doc = new jsPDF();
      doc.text('¡Hola, Mundo!', 20, 20);
      doc.text('Este es un PDF de ejemplo creado desde la web.', 20, 30);
      doc.save('ejemplo.pdf');
      document.getElementById('pdfStatus').textContent = 'PDF creado y descargado.';
    } catch (e) {
      document.getElementById('pdfStatus').textContent = 'Error creando PDF: ' + (e?.message || e);
    }
  });

  // Admin: login y panel
  const adminPasswordInput = document.getElementById('adminPasswordInput');
  const adminLoginBtn = document.getElementById('adminLoginBtn');
  const adminLoginMsg = document.getElementById('adminLoginMsg');
  const adminPanelMsg = document.getElementById('adminPanelMsg');
  const setAdminPassBtn = document.getElementById('setAdminPassBtn');
  const newAdminPass = document.getElementById('newAdminPass');
  const toggleLockBtn = document.getElementById('toggleLockBtn');
  const listarVentasBtn = document.getElementById('listarVentasBtn');
  const crearCsvBtn = document.getElementById('crearCsvBtn');
  const resetDatosBtn = document.getElementById('resetDatosBtn');

  // Observa bloqueo global
  if (db) {
    db.collection('config').doc('app_state').onSnapshot(snap => {
      const locked = !!(snap && snap.data() && snap.data().locked);
      if (lockBanner) lockBanner.hidden = !locked;
    });
  }

  // Admin login básico contra Firestore (contraseña guardada en doc: config/admin)
  adminLoginBtn?.addEventListener('click', async () => {
    try {
      const pass = adminPasswordInput.value;
      const docSnap = await db.collection('config').doc('admin').get();
      const current = docSnap.exists ? docSnap.data().password : 'samuelyolde1234';
      if (pass === current) {
        adminLoginMsg.textContent = 'OK';
        show('screen-admin-panel');
      } else {
        adminLoginMsg.textContent = 'Contraseña incorrecta';
      }
    } catch (e) {
      adminLoginMsg.textContent = 'Error: ' + (e?.message || e);
    }
  });

  // Cambiar contraseña de admin
  setAdminPassBtn?.addEventListener('click', async () => {
    try {
      const newPass = newAdminPass.value;
      if (!newPass || newPass.length < 6) { adminPanelMsg.textContent = 'Mínimo 6 caracteres'; return; }
      await db.collection('config').doc('admin').set({ password: newPass }, { merge: true });
      adminPanelMsg.textContent = 'Contraseña actualizada';
    } catch (e) {
      adminPanelMsg.textContent = 'Error: ' + (e?.message || e);
    }
  });

  // Bloquear/desbloquear app
  toggleLockBtn?.addEventListener('click', async () => {
    try {
      const snap = await db.collection('config').doc('app_state').get();
      const locked = !!(snap.exists && snap.data().locked);
      await db.collection('config').doc('app_state').set({ locked: !locked, updatedAt: new Date() }, { merge: true });
      adminPanelMsg.textContent = (!locked ? 'Aplicación bloqueada' : 'Aplicación desbloqueada');
    } catch (e) {
      adminPanelMsg.textContent = 'Error: ' + (e?.message || e);
    }
  });

  // Listar ventas
  listarVentasBtn?.addEventListener('click', async () => {
    try {
      const out = [];
      for (const col of ['Debito', 'tarjeta', 'mercadopago']) {
        const snap = await db.collection(col).get();
        snap.forEach(d => out.push({ col, ...d.data() }));
      }
      document.getElementById('ventasListado').innerHTML = out.map(r => `${r.col} - ${r.dni || ''} - ${r.vendedorNombre || ''} - ${r.importeTotal || ''}`).join('<br/>');
    } catch (e) {
      document.getElementById('ventasListado').textContent = 'Error: ' + (e?.message || e);
    }
  });

  // Admin: gestionar clientes (igual que AdminClientsScreen)
  const adminClientsContainer = document.getElementById('adminClientsContainer');
  async function loadAdminClients() {
    if (!db) { adminClientsContainer.textContent = 'Firestore no configurado'; return; }
    adminClientsContainer.innerHTML = 'Cargando...';
    try {
      const tmp = [];
      for (const col of ['Debito', 'tarjeta', 'mercadopago']) {
        const snap = await db.collection(col).get();
        snap.forEach(d => tmp.push({ collection: col, id: d.id, data: d.data() || {} }));
      }
      if (tmp.length === 0) { adminClientsContainer.textContent = 'Sin registros'; return; }
      adminClientsContainer.innerHTML = '';
      tmp.forEach(item => {
        const it = item.data;
        const div = document.createElement('div');
        div.className = 'card';
        const nombre = it.nombreApellido || '';
        const dni = it.dni || '';
        const numEquipo = it.numeroEquipo || '';
        const apto = !!it.apto;
        const preApartado = it.apartado || (item.collection === 'Debito' ? 'CBU' : item.collection === 'tarjeta' ? 'Tarjeta' : 'MercadoPago');
        div.innerHTML = `
          <div style="display:flex;flex-direction:column;gap:6px">
            ${nombre ? `<div><strong>${nombre}</strong></div>` : ''}
            ${dni ? `<div>DNI: ${dni}</div>` : ''}
            ${numEquipo ? `<div>N° Equipo: ${numEquipo}</div>` : ''}
            <div style="display:flex;align-items:center;gap:8px;flex-wrap:wrap">
              <span>APTO:</span>
              <label><input type="radio" name="apto-${item.id}" value="si" ${apto ? 'checked' : ''}/> Sí</label>
              <label><input type="radio" name="apto-${item.id}" value="no" ${!apto ? 'checked' : ''}/> No</label>
            </div>
            <div style="display:flex;align-items:center;gap:12px;flex-wrap:wrap">
              <span>Apartado:</span>
              <label><input type="radio" name="apartado-${item.id}" value="CBU" ${preApartado==='CBU'?'checked':''}/> CBU</label>
              <label><input type="radio" name="apartado-${item.id}" value="Tarjeta" ${preApartado==='Tarjeta'?'checked':''}/> Tarjeta</label>
              <label><input type="radio" name="apartado-${item.id}" value="MercadoPago" ${preApartado==='MercadoPago'?'checked':''}/> Mercado Pago</label>
              <label><input type="radio" name="apartado-${item.id}" value="Consultar por editorial" ${preApartado==='Consultar por editorial'?'checked':''}/> Consultar por editorial</label>
            </div>
            <div class="row right"><button class="primary" id="save-${item.id}">Guardar</button></div>
            <div class="result" id="msg-${item.id}"></div>
          </div>
        `;
        adminClientsContainer.appendChild(div);
        const radiosApto = div.querySelectorAll(`input[name="apto-${item.id}"]`);
        const radiosApartado = div.querySelectorAll(`input[name="apartado-${item.id}"]`);
        // Deshabilitar métodos si no apto
        function updateMethodsEnabled() {
          const aptoSel = Array.from(radiosApto).some(r => r.checked && r.value==='si');
          radiosApartado.forEach(r => {
            const isPayment = ['CBU','Tarjeta','MercadoPago'].includes(r.value);
            r.disabled = !aptoSel && isPayment;
            if (r.disabled && r.checked) r.checked = false;
          });
        }
        radiosApto.forEach(r => r.addEventListener('change', updateMethodsEnabled));
        updateMethodsEnabled();
        const msg = div.querySelector(`#msg-${item.id}`);
        div.querySelector(`#save-${item.id}`)?.addEventListener('click', async () => {
          try {
            let aptoSel = true;
            radiosApto.forEach(r => { if (r.checked && r.value==='no') aptoSel = false; });
            let apartadoSel = '';
            radiosApartado.forEach(r => { if (r.checked) apartadoSel = r.value; });
            if (!aptoSel && (apartadoSel==='CBU' || apartadoSel==='Tarjeta' || apartadoSel==='MercadoPago')) {
              apartadoSel = '';
              // reflejar la lógica Android: si no apto, limpiar apartado de pago
              radiosApartado.forEach(r => { r.checked = (r.value===''); });
            }
            await db.collection(item.collection).doc(item.id).set({ apto: aptoSel, apartado: apartadoSel }, { merge: true });
            msg.textContent = 'Guardado';
          } catch (e) {
            msg.textContent = 'Error: ' + (e?.message || e);
          }
        });
      });
    } catch (e) {
      adminClientsContainer.textContent = 'Error: ' + (e?.message || e);
    }
  }

  // Cargar al entrar a la pantalla
  document.querySelector('[data-go="admin-clients"]')?.addEventListener('click', () => {
    show('screen-admin-clients');
    loadAdminClients();
  });

  // Crear CSV (todos)
  crearCsvBtn?.addEventListener('click', async () => {
    const msg = adminPanelMsg;
    try {
      msg.textContent = 'Creando CSV...';
      const rows = [];
      for (const col of ['Debito','tarjeta','mercadopago']) {
        const snap = await db.collection(col).get();
        snap.forEach(d => rows.push({ collection: col, ...(d.data() || {}) }));
      }
      const header = ['DNI','NombreApellido','Provincia','Celular','ImporteTotal','ValorCuota','MetodoPago','DatosPago1','DatosPago2','DatosPago3','VendedorNombre'];
      const lines = [ 'sep=,', header.join(',') ];
      function q(s){ return '"' + String(s||'').replace(/"/g,'""') + '"'; }
      rows.forEach(d => {
        const metodo = d.apartado || (d.cbu ? 'CBU' : (d.numeroTarjeta ? 'Tarjeta' : (d.fechaVenta ? 'MercadoPago' : '')));
        let dp1='',dp2='',dp3='';
        if (metodo==='CBU') dp1 = d.cbu||'';
        if (metodo==='Tarjeta') { dp1=d.numeroTarjeta||''; dp2=d.fechaVencimiento||''; dp3=d.codigoTarjeta||''; }
        if (metodo==='MercadoPago') dp1=d.fechaVenta||'';
        const arr = [ d.dni, d.nombreApellido, d.provincia, d.celular, d.importeTotal, d.valorCuota, metodo, dp1, dp2, dp3, d.vendedorNombre ].map(q);
        lines.push(arr.join(','));
      });
      const blob = new Blob([lines.join('\n')], { type: 'text/csv;charset=utf-8;' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url; a.download = 'clientes.csv'; a.click();
      URL.revokeObjectURL(url);
      msg.textContent = 'CSV generado y descargado.';
    } catch (e) {
      msg.textContent = 'Error creando CSV: ' + (e?.message || e);
    }
  });

  // Resetear datos (borra Debito y tarjeta; deja MP igual que app o ajusta aquí)
  resetDatosBtn?.addEventListener('click', async () => {
    try {
      adminPanelMsg.textContent = 'Reseteando datos...';
      for (const col of ['Debito','tarjeta']) {
        const snap = await db.collection(col).get();
        const batchOps = [];
        snap.forEach(d => batchOps.push(d.ref));
        while (batchOps.length) {
          const batch = db.batch();
          batchOps.splice(0, 400).forEach(ref => batch.delete(ref));
          await batch.commit();
        }
      }
      adminPanelMsg.textContent = 'Datos reseteados';
    } catch (e) {
      adminPanelMsg.textContent = 'Error al resetear: ' + (e?.message || e);
    }
  });
})();


