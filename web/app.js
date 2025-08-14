(function () {
  // 1) Configurar Firebase Web
  // Reemplaza el siguiente objeto con tu configuración de Firebase (Firebase Console -> Project settings -> Your apps -> Web app)
  const firebaseConfig = {
    apiKey: "REEMPLAZAR",
    authDomain: "REEMPLAZAR",
    projectId: "REEMPLAZAR",
    storageBucket: "REEMPLAZAR",
    messagingSenderId: "REEMPLAZAR",
    appId: "REEMPLAZAR"
  };

  try {
    firebase.initializeApp(firebaseConfig);
  } catch (e) {
    console.warn("Firebase init warning:", e?.message || e);
  }

  const db = (firebase && firebase.firestore) ? firebase.firestore() : null;

  // 2) Selectores
  const dniInput = document.getElementById('dniInput');
  const buscarBtn = document.getElementById('buscarBtn');
  const resultadoBusqueda = document.getElementById('resultadoBusqueda');

  const dniCuil = document.getElementById('dniCuil');
  const genero = document.getElementById('genero');
  const calcularBtn = document.getElementById('calcularBtn');
  const resultadoCuil = document.getElementById('resultadoCuil');

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
  if (buscarBtn) {
    buscarBtn.addEventListener('click', async () => {
      const dni = sanitizeDni(dniInput.value);
      if (!dni) {
        resultadoBusqueda.textContent = 'Ingrese un DNI válido.';
        return;
      }

      if (!db) {
        resultadoBusqueda.textContent = 'Firestore no está configurado. Edite web/app.js con su configuración.';
        return;
      }

      resultadoBusqueda.textContent = 'Buscando...';
      try {
        // Primero búsqueda exacta por campo dni
        const q1 = await db.collection('clientes').where('dni', '==', dni).limit(1).get();
        if (!q1.empty) {
          const doc = q1.docs[0];
          const data = doc.data();
          resultadoBusqueda.innerHTML = `<strong>${data?.nombre || 'Sin nombre'}</strong><br/>Estado: ${data?.estado || '-'}<br/>Hoja: ${data?.hoja || '-'}`;
          return;
        }

        // Intento con ceros a la izquierda (padded)
        const padded = dni.padStart(8, '0');
        const q2 = await db.collection('clientes').where('dni', '==', padded).limit(1).get();
        if (!q2.empty) {
          const doc = q2.docs[0];
          const data = doc.data();
          resultadoBusqueda.innerHTML = `<strong>${data?.nombre || 'Sin nombre'}</strong><br/>Estado: ${data?.estado || '-'}<br/>Hoja: ${data?.hoja || '-'}`;
          return;
        }

        // Búsqueda parcial por prefijo (requiere índice compuesto si el dataset es grande)
        const q3 = await db.collection('clientes')
          .orderBy('dni')
          .startAt(dni)
          .endAt(dni + '\uf8ff')
          .limit(10)
          .get();
        if (!q3.empty) {
          const rows = q3.docs.map(d => d.data());
          resultadoBusqueda.innerHTML = rows.map(r => `${r.dni} - ${r.nombre} (${r.estado || '-'})`).join('<br/>');
          return;
        }

        resultadoBusqueda.textContent = 'DNI apto (no encontrado en incobrables).';
      } catch (err) {
        console.error(err);
        resultadoBusqueda.textContent = 'Error en la búsqueda: ' + (err?.message || err);
      }
    });
  }

  if (calcularBtn) {
    calcularBtn.addEventListener('click', () => {
      resultadoCuil.textContent = calculateCuil(dniCuil.value, genero.value);
    });
  }
})();


