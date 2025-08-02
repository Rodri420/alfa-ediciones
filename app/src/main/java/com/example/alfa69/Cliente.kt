package com.example.alfa69

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "clientes")
data class Cliente(
    @PrimaryKey val dni: String,
    val nombre: String,
    val estado: String,
    val hoja: String
) 