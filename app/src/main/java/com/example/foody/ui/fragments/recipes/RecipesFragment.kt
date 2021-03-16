package com.example.foody.ui.fragments.recipes

import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.foody.R
import com.example.foody.adapters.RecipesAdapter
import com.example.foody.databinding.FragmentRecipesBinding
import com.example.foody.util.NetWorkListener
import com.example.foody.util.NetworkResult
import com.example.foody.util.observeOnce
import com.example.foody.viewmodels.MainViewModel
import com.example.foody.viewmodels.RecipesViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.android.synthetic.main.fragment_recipes.view.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@ExperimentalCoroutinesApi
@AndroidEntryPoint
class RecipesFragment : Fragment(), SearchView.OnQueryTextListener {

    private val args by navArgs<RecipesFragmentArgs>()

    private var _binding: FragmentRecipesBinding? = null
    private val binding get() = _binding!!

    private lateinit var mainViewModel: MainViewModel
    private lateinit var recipesViewModel: RecipesViewModel

    private lateinit var netWorkListener: NetWorkListener

    private val mAdapter by lazy { RecipesAdapter() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mainViewModel = ViewModelProvider(requireActivity()).get(MainViewModel::class.java)
        recipesViewModel = ViewModelProvider(requireActivity()).get(RecipesViewModel::class.java)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        _binding = FragmentRecipesBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = this
        binding.mainViewModel = mainViewModel

        setHasOptionsMenu(true)

        setupRecyclerView()

        recipesViewModel.readBackOnline.observe(viewLifecycleOwner, {
            recipesViewModel.backOnline = it
        })

        lifecycleScope.launch {
            netWorkListener = NetWorkListener()
            netWorkListener.checkNetworkAvailability(requireContext())
                .collect { status ->
                    Log.d("NetworkListener", status.toString())
                    recipesViewModel.netWorkStatus = status
                    recipesViewModel.showNetWorkStatus()
                    readDatabase()
                }
        }

        binding.recipesFab.setOnClickListener {
            if (recipesViewModel.netWorkStatus) {
                findNavController().navigate(R.id.action_recipesFragment_to_recipesBottomSheet)
            } else {
                recipesViewModel.showNetWorkStatus()
            }
        }

        return binding.root
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        if (query != null) {
            searchApiData(query)
        }
        return true
    }

    override fun onQueryTextChange(p0: String?): Boolean {
        return true
    }

    private fun setupRecyclerView() {
        binding.recyclerview.apply {
            adapter = mAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }

        showShimmerEffect()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.recipes_menu, menu)

        val search = menu.findItem(R.id.menu_search)
        val searchView = search.actionView as? SearchView
        searchView?.isSubmitButtonEnabled = true
        searchView?.setOnQueryTextListener(this)
    }

    private fun searchApiData(searchQuery: String) {
        showShimmerEffect()
        mainViewModel.searchRecipes(recipesViewModel.applySearchQuery(searchQuery))
        mainViewModel.searchedRecipesResponse.observe(viewLifecycleOwner, { response ->
            when (response) {
                is NetworkResult.Success -> {
                    hideShimmerEffect()
                    val foodRecipe = response.data
                    foodRecipe?.let { mAdapter.setDate(it) }
                }
                is NetworkResult.Error -> {
                    hideShimmerEffect()
                    loadDataFromCache()
                    Toast.makeText(
                        requireContext(),
                        response.message.toString(),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                is NetworkResult.Loading -> {
                    showShimmerEffect()
                }
            }
        })
    }

    private fun loadDataFromCache() {
        lifecycleScope.launch {
            mainViewModel.readRecipes.observe(viewLifecycleOwner, { database ->
                if (database.isNotEmpty()) {
                    mAdapter.setDate(database[0].foodRecipe)
                }
            })
        }
    }

    private fun requestApiData() {
        Log.d("RecipesFragment", "RequestApiData called!")
        mainViewModel.getRecipes(recipesViewModel.applyQueries())
        mainViewModel.recipesResponse.observe(viewLifecycleOwner, { response ->
            when (response) {
                is NetworkResult.Success -> {
                    hideShimmerEffect()
                    response.data?.let { mAdapter.setDate(it) }
                }
                is NetworkResult.Error -> {
                    hideShimmerEffect()
                    loadDataFromCache()
                    Toast.makeText(
                        requireContext(),
                        response.message.toString(),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                is NetworkResult.Loading -> {
                    showShimmerEffect()
                }

            }

        })

    }

    private fun readDatabase() {
        Log.d("RecipesFragment", "ReadDatabase called!")

        lifecycleScope.launch {
            mainViewModel.readRecipes.observeOnce(viewLifecycleOwner, { database ->
                if (database.isNotEmpty() && args.backFromBottomSheet.not()) {
                    mAdapter.setDate(database[0].foodRecipe)
                    hideShimmerEffect()
                    return@observeOnce
                }
                requestApiData()
            })
        }
    }

    private fun showShimmerEffect() {
        binding.recyclerview.showShimmer()
    }

    private fun hideShimmerEffect() {
        binding.recyclerview.hideShimmer()
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}