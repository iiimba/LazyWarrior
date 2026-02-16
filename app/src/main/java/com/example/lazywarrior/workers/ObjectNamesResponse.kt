package com.example.lazywarrior.workers

data class ObjectNamesResponse(
    val range: String,
    val majorDimension: String,
    val values: List<List<String>>
)
