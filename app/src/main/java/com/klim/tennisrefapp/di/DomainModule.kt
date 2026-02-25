package com.klim.tennisrefapp.di

import com.klim.tennisrefapp.domain.usecase.ApplyViolationUseCase
import com.klim.tennisrefapp.domain.usecase.ComputeNotBeforeUseCase
import com.klim.tennisrefapp.domain.usecase.ResolveSlotUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object DomainModule {
    @Provides
    fun provideResolveSlotUseCase(): ResolveSlotUseCase = ResolveSlotUseCase()

    @Provides
    fun provideComputeNotBeforeUseCase(): ComputeNotBeforeUseCase = ComputeNotBeforeUseCase()

    @Provides
    fun provideApplyViolationUseCase(): ApplyViolationUseCase = ApplyViolationUseCase()
}
