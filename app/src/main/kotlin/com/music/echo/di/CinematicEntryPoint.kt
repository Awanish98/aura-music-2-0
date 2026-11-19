package echo.music.iad1tya.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import echo.music.iad1tya.generate.GenresRepository
import echo.music.iad1tya.smart.SmartCinematicRepository

@EntryPoint
@InstallIn(SingletonComponent::class)
interface CinematicEntryPoint {
  fun genresRepository(): GenresRepository

  fun smartCinematicRepository(): SmartCinematicRepository
}
