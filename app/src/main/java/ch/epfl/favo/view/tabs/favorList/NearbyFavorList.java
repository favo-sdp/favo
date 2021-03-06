package ch.epfl.favo.view.tabs.favorList;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ch.epfl.favo.R;
import ch.epfl.favo.favor.Favor;
import ch.epfl.favo.util.CommonTools;
import ch.epfl.favo.util.DependencyFactory;
import ch.epfl.favo.viewmodel.IFavorViewModel;

import static androidx.navigation.Navigation.findNavController;

/**
 * View will contain list of favors requested by users nearby. The list will contain clickable items
 * that will expand to give more information about them. This object is a simple {@link Fragment}
 * subclass.
 */
// @RequiresApi(api = Build.VERSION_CODES.N)
public class NearbyFavorList extends Fragment {

  private View rootView;
  private RecyclerView recycleView;
  private TextView tipTextView;

  private SearchView searchView;
  private MenuItem searchMenuItem;

  private Map<String, Favor> favorsFound = new HashMap<>();
  private Map<String, Favor> nearbyFavors;

  private IFavorViewModel viewModel;

  public NearbyFavorList() {
    // Required empty public constructor
  }

  @Override
  public void onCreate(Bundle bundle) {
    super.onCreate(bundle);
    setHasOptionsMenu(true);
  }

  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    rootView = inflater.inflate(R.layout.fragment_nearby_favor_list, container, false);
    setupView(rootView);

    tipTextView = rootView.findViewById(R.id.nearby_tip);
    tipTextView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);

    recycleView = rootView.findViewById(R.id.nearby_favor_list);
    recycleView.addItemDecoration(
        new DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL));
    setupListView();

    RadioButton toggle = rootView.findViewById(R.id.map_switch);
    toggle.setOnClickListener(this::onToggleClick);

    viewModel =
        (IFavorViewModel)
            new ViewModelProvider(requireActivity())
                .get(DependencyFactory.getCurrentViewModelClass());

    return rootView;
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    setupNearbyFavorsListener();
  }

  private void setupNearbyFavorsListener() {
    getViewModel()
        .getFavorsAroundMe()
        .observe(
            getViewLifecycleOwner(),
            stringFavorMap -> {
              try {
                nearbyFavors = stringFavorMap;
                displayFavorList(nearbyFavors, R.string.favor_no_nearby_favor);
              } catch (Exception e) {
                CommonTools.showSnackbar(rootView, getString(R.string.error_database_sync));
              }
            });
  }

  /*
   * @return favor view model
   */
  public IFavorViewModel getViewModel() {
    return viewModel;
  }

  @Override
  public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {

    // Inflate the menu; this adds items to the action bar if it is present.
    inflater.inflate(R.menu.favor_list_menu, menu);
    super.onCreateOptionsMenu(menu, inflater);

    searchMenuItem = menu.findItem(R.id.search_item);
    searchView = (androidx.appcompat.widget.SearchView) searchMenuItem.getActionView();
    setupSearchListeners();
  }

  private void onToggleClick(View view) {
    findNavController(requireActivity(), R.id.nav_host_fragment).popBackStack(R.id.nav_map, false);
  }

  private void setupSearchMode() {
    rootView.findViewById(R.id.toggle).setVisibility(View.INVISIBLE);
    searchView.setOnQueryTextListener(new onQueryListener());
    displayFavorList(favorsFound, R.string.empty);
  }

  private void quitSearchMode() {
    rootView.findViewById(R.id.toggle).setVisibility(View.VISIBLE);
    favorsFound.clear();
    searchView.setOnQueryTextListener(null);
    displayFavorList(nearbyFavors, R.string.empty);
  }

  /**
   * Display the list of favors specified by the argument, or display an tip string.
   *
   * @param favors: a map of [favor_id, favor] to be displayed
   * @param textId: ID of the string to be displayed by tipTextView when favor list is empty
   */
  private void displayFavorList(Map<String, Favor> favors, int textId) {
    if (favors.isEmpty()) {
      tipTextView.setText(getString(textId));
      tipTextView.setVisibility(View.VISIBLE);
    } else tipTextView.setVisibility(View.INVISIBLE);

    List<Favor> favorList = new ArrayList<>(favors.values());
    recycleView.setAdapter(createFavorAdapter(favorList));
  }

  private void setupSearchListeners() {
    searchMenuItem.setOnActionExpandListener(
        new MenuItem.OnActionExpandListener() {
          @Override
          public boolean onMenuItemActionExpand(MenuItem item) {
            setupSearchMode();
            return true;
          }

          @Override
          public boolean onMenuItemActionCollapse(MenuItem item) {
            quitSearchMode();
            return true;
          }
        });
  }

  class onQueryListener implements SearchView.OnQueryTextListener {
    @Override
    public boolean onQueryTextSubmit(String query) {
      favorsFound = CommonTools.findFavorByTitleDescription(query, nearbyFavors);
      displayFavorList(favorsFound, R.string.query_failed);
      return false;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
      if (newText.equals("")) favorsFound = new HashMap<>();
      else favorsFound = CommonTools.findFavorByTitleDescription(newText, nearbyFavors);
      displayFavorList(favorsFound, R.string.query_failed);
      return false;
    }
  }

  /**
   * Set up onItemClickListener for list items. Specifically, when a favor item is clicked, 1. High
   * the soft keyboard. 2. Extract the favor object and put it inside a bundle with key
   * CommonTools.FAVOR_ARGS 3. Navigate to favorPublishView with this bundle.
   */
  private void setupListView() {
    recycleView.setLayoutManager(new LinearLayoutManager(requireContext()));
    recycleView.setAdapter(createFavorAdapter(new ArrayList<>()));
  }

  private RecyclerView.Adapter<FavorViewHolder> createFavorAdapter(List<Favor> items) {
    return new RecyclerView.Adapter<FavorViewHolder>() {
      @NonNull
      @Override
      public FavorViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view =
            LayoutInflater.from(parent.getContext())
                .inflate(R.layout.favor_list_item, parent, false);

        view.setOnClickListener(
            v -> {
              int itemPosition = recycleView.getChildLayoutPosition(view);
              Favor favor = items.get(itemPosition);
              if (favor != null) {
                Bundle favorBundle = new Bundle();
                favorBundle.putString(CommonTools.FAVOR_ARGS, favor.getId());
                Navigation.findNavController(requireView())
                    .navigate(R.id.action_nav_nearby_list_to_favorPublishedView, favorBundle);
              }
            });

        return new FavorViewHolder(view);
      }

      @Override
      public void onBindViewHolder(@NonNull FavorViewHolder holder, int position) {
        Favor favor = items.get(position);
        if (favor != null) {
          holder.bind(requireContext(), favor);
        }
      }

      @Override
      public int getItemCount() {
        return items.size();
      }
    };
  }

  /**
   * Ensure click on the view will hide keyboard
   *
   * @param view: clicking on this view will hide the soft keyboard
   */
  @SuppressLint("ClickableViewAccessibility")
  private void setupView(View view) {
    view.findViewById(R.id.constraint_layout_nearby_favor_view)
        .setOnTouchListener(
            (v, event) -> {
              CommonTools.hideSoftKeyboard(requireActivity());
              return false;
            });
  }
}
