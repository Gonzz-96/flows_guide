package com.example.flows.main

import com.example.flows.error.ResultWrapper
import com.example.flows.extensions.safeApiCall
import com.example.flows.main.data.ApiResponse
import com.example.flows.main.data.Dog
import com.example.flows.main.local.DogDao
import com.example.flows.main.network.MainActivityApi
import com.example.flows.main.network.RemoteDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

class MainActivityRepository @Inject constructor(
    private val dogDao: DogDao,
    private val dogsRDS: RemoteDataSource,
    private val api: MainActivityApi
) {

//    @ExperimentalCoroutinesApi
//    val dogListFlow: Flow<List<Dog>>
//        get() = dogDao.loadAllEpisodesFlow()
//            .combine(topBreedsFlow) { dogs, topDogs ->
//                dogs.applyToDog(topDogs)
//            }
//            .flowOn(Dispatchers.Default)
//            .conflate()

    val dogListFlow: Flow<List<Dog>>
        get() = dogDao.loadAllEpisodesFlow()

    @ExperimentalCoroutinesApi
    fun getSearchedDogs(search: String): Flow<List<Dog>> {
        return dogDao.getEpisodesForTrilogyNumberFlow(search)
            .combine(topBreedsFlow) { dogs, topDogs ->
                dogs.applyToDog(topDogs)
            }
            .flowOn(Dispatchers.Default)
            .conflate()
    }

    suspend fun tryUpdateDogCache() {
        val dogApiResponse = dogsRDS.fetchRandomDog()
        dogDao.save(dogApiResponse)
    }


    //emit at once
//    private val topBreedsFlow = flow {
//
//        val topBreedsList = dogsRDS.favoritesSortOrder()
//        emit(topBreedsList)
//    }

    private val topBreedsFlow = dogsRDS.favoritesSortOrder()


    suspend fun tryFetchAndUpdate(): ResultWrapper {

        val api = safeApiCall(Dispatchers.IO) { api.getRandomImageByUrl() }
        when (api) {
            is ResultWrapper.Success<*> -> {
                val dogResponse = api.value as ApiResponse<String>
                val breedImageUrl = dogResponse.message
                val dog = extractBreedName(breedImageUrl)?.let { Dog(it, breedImageUrl) }
                dog?.run {
                    dogDao.save(this)
                }
            }
        }
        return api
    }

    private fun extractBreedName(message: String): String? {
        val breedName = message.substringAfter("breeds/").substringBefore("/")
        return breedName.replace(Regex("-"), " ").capitalize()
    }


    private fun List<Dog>.applyToDog(favoritesSortOrder: List<String>): List<Dog> {
        return this.map {
            val isTopDog = favoritesSortOrder.contains(it.breed.capitalize())
            Dog(it.breed, it.imageUrl, isTopDog)
        }
    }

    suspend fun clearCacheData() {
        try {
            dogDao.deleteCache()
        } catch (error: Throwable) {

        }
    }

    suspend fun fetchAndUpdate(): ResultWrapper {
        val wrappedResult = safeApiCall(Dispatchers.IO) { api.getRandomDogBreed() }
        when (wrappedResult) {
            is ResultWrapper.Success<*> -> {
                val dogResponse = wrappedResult.value as ApiResponse<String>
                val breedImageUrl = dogResponse.message
                val dog = extractBreedName(breedImageUrl)?.let { Dog(it, breedImageUrl) }
                dog?.run {
                    dogDao.save(this)
                }
            }
        }
        return wrappedResult
    }
}
