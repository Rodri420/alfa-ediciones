package com.example.alfa69

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ClienteDao {
    @Query("SELECT * FROM clientes WHERE dni = :dni")
    suspend fun buscarPorDni(dni: String): Cliente?
    
    @Query("SELECT * FROM clientes WHERE dni LIKE '%' || :dni || '%'")
    suspend fun buscarPorDniParcial(dni: String): List<Cliente>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarCliente(cliente: Cliente)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarClientes(clientes: List<Cliente>)
    
    @Query("DELETE FROM clientes")
    suspend fun eliminarTodos()
    
    @Query("SELECT COUNT(*) FROM clientes")
    suspend fun contarClientes(): Int
} 