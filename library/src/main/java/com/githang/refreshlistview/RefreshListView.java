package com.githang.refreshlistview;

import android.content.Context;
import android.content.res.TypedArray;
import android.database.DataSetObserver;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.AbsListView;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * 带有下拉刷新及上拉加载的ListView.
 *
 * 代码改自网上的代码，原作者为：https://github.com/liuguangmao.
 * 原来的代码貌似是基于johannilsson/android-pulltorefresh修改的.
 *
 * @author 黄浩杭 (msdx.android[at]qq[dot]com)
 */
public class RefreshListView extends ListView implements AbsListView.OnScrollListener {

    /**
     * 控件状态-下拉过界
     */
    private static final int RELEASE_TO_REFRESH = 0;
    /**
     * 控件状态-下拉中
     */
    private static final int PULL_TO_REFRESH = 1;
    /**
     * 控件状态-正在加载
     */
    private static final int REFRESHING = 2;

    /**
     * 控件状态-加载完成||无需加载状态
     */
    private static final int DONE = 3;

    /**
     * 控件状态-上拉刷新
     */
    private static final int RAISE_TO_REFRESH = 5;

    /**
     * 控件状态-上拉时松手刷新
     */
    private static final int RELEASE_RAISE_TO_REFRESH = 6;

    /**
     * 滑动距离/提示信息高度 的比值
     */
    private static final int RATIO = 2;

    /**
     * 上下文
     */
    private Context mContext;

    /**
     * 下拉提示控件
     */
    private View mRefreshView;

    /**
     * 提示信息所在的文本控件
     */
    private TextView mRefreshTextView;

    /**
     * 提示控件的时间显示文本控件
     */
    private TextView mRefreshTimeView;

    /**
     * 进度条
     */
    private ProgressBar mRefreshProgressBar;

    /**
     * 下拉箭头控件
     */
    private ImageView mArrowImageView;

    /**
     * 底部提示框
     */
    private View mLoadMoreView;

    /**
     * 底部提示信息
     */
    private TextView mLoadMoreTextView;

    /**
     * 没有更多数据的提示信息
     */
    private TextView mNoDataTextView;

    /**
     * 底部进度条
     */
    private View mLoadMoreProgressView;

    private boolean mNeedShowNoData;

    /**
     * 当前状态
     */
    private int mPullRefreshState;

    /**
     * 下拉提示控件高度
     */
    private int mHeaderHeight;

    /**
     * 上拉提示控件高度
     */
    private int mFooterHeight;

    /**
     * 箭头翻转动画
     */
    private RotateAnimation animation;

    /**
     * 下拉箭头翻转动画
     */
    private RotateAnimation reverseAnimation;

    /**
     * 下拉越界后是否有拖回界限内
     */
    private boolean isBack;

    /**
     * 第一个可见条目的序号
     */
    private int firstItemIndex;

    /**
     * 总条目数
     */
    private int totalSize;

    /**
     * 最后一个可见条目的序号
     */
    private boolean isFootBarWork = true;

    /**
     * 是否已开始记录事件位置
     */
    private boolean isRecord;

    /**
     * 触摸事件起始位置
     */
    private int startY;

    /**
     * 界限倍数
     */
    private int raiseBoundary = 3;

    /**
     * 刷新时的回调监听
     */
    private OnRefreshLoadListener mOnRefreshLoadListener;

    /**
     * 是否初始化头部信息栏
     */
    private boolean isInitRefresh;

    /**
     * 是否初始化底部信息栏
     */
    private boolean isInitLoadMore;

    /**
     * 是否启用上拉加载
     */
    private boolean mCanLoadMore = true;

    /**
     * 箭头动画是否旋转
     */
    private boolean mArrowAnimationEnabled = true;

    private boolean mAutoLoadEnabled;

    private int mPaddingTop;
    private int mPaddingBottom;

    /**
     * 简单构造方法
     *
     * @param context 上下文
     */
    public RefreshListView(Context context) {
        super(context);
        this.mContext = context;
        if (isInEditMode()) {
            return;
        }
        initHeader();
        initFooter();
        isInitRefresh = true;
        isInitLoadMore = true;
        setOnScrollListener(this);
    }

    /**
     * 系统调用的构造方法 当在xml中配置本控件时调用
     *
     * @param context 上下文
     * @param attrs   xml中配置的参数
     */
    public RefreshListView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.mContext = context;
        TypedArray array = context.obtainStyledAttributes(attrs,
                R.styleable.RefreshListView);
        isInitRefresh = array.getBoolean(
                R.styleable.RefreshListView_init_header, true);
        isInitLoadMore = array.getBoolean(
                R.styleable.RefreshListView_init_footer, true);
        if (isInEditMode()) {
            return;
        }
        if (isInitRefresh) {
            initHeader();
        }

        if (isInitLoadMore) {
            initFooter();
        }
        setOnScrollListener(this);
        array.recycle();
        mPaddingBottom = getPaddingTop();
        mPaddingBottom = getPaddingBottom();
    }

    /**
     * 设置刷新监听
     *
     * @param listener 刷新监听器
     */
    public void setOnRefreshLoadListener(OnRefreshLoadListener listener) {
        this.mOnRefreshLoadListener = listener;
    }

    /**
     * 设置上拉时触动加载更多的边界 默认为2倍底部信息栏高度
     *
     * @param boundary 界限值与底部信息栏的比值
     */
    public void setLoadMoreBoundary(int boundary) {
        if (boundary <= 1) {
            throw new IllegalArgumentException("该比值不可小于1");
        }
        this.raiseBoundary = boundary;
    }

    /**
     * 设置顶部信息栏不可见 若在配置文件中配置为可见，需在设置适配器之后调用
     */
    public void removeRefreshView() {
        if (isInitRefresh) {
            if (mRefreshView != null) {
                this.removeHeaderView(mRefreshView);
            }
        }
        isInitRefresh = false;
    }

    /**
     * 设置底部信息栏不可见 若在配置文件中配置为可见，需在设置适配器之后调用
     */
    public void removeLoadMoreView() {
        if (isInitLoadMore) {
            if (mLoadMoreView != null) {
                this.removeFooterView(mLoadMoreView);
            }
            isInitLoadMore = false;
        }
    }

    /**
     * 设置上拉加载是否可用
     *
     * @param noMoreData 上拉加载是否可用
     */
    public void setNoMoreData(boolean noMoreData) {
        setCanLoadMore(!noMoreData);
        if (noMoreData) {
            if (mNeedShowNoData) {
                mNoDataTextView.setVisibility(VISIBLE);
                mLoadMoreView.setOnClickListener(null);
            } else {
                mNoDataTextView.setVisibility(GONE);
            }
        } else {
            mNoDataTextView.setVisibility(GONE);
        }
    }

    /**
     * 设置上拉加载是否可用
     *
     * @param canLoadMore 上拉加载是否可用
     */
    public void setCanLoadMore(boolean canLoadMore) {
        mCanLoadMore = canLoadMore;
        if (canLoadMore) {
            changeFooterViewByState();
            mLoadMoreView.setOnClickListener(mFootLoadMoreListener);
        } else {
            mLoadMoreTextView.setVisibility(GONE);
            mLoadMoreProgressView.setVisibility(GONE);
        }
    }

    /**
     * 设置是否启用下拉加载的提示图标的动画
     *
     * @param enabled
     */
    public void setArrowAnimationEnabled(boolean enabled) {
        mArrowAnimationEnabled = enabled;
    }

    public void setAutoLoadEnabled(boolean autoLoadEnabled) {
        mAutoLoadEnabled = autoLoadEnabled;
    }

    public void setUpdateTimeEnabled(boolean enabled) {
        mRefreshTimeView.setVisibility(enabled ? VISIBLE : GONE);
    }

    /**
     * 没有更多数据可被加载的提示信息
     *
     * @param noDataMsg 提示信息，如：没有更多数据了
     */
    public void setNoDataMsg(String noDataMsg) {
        mNoDataTextView.setText(noDataMsg);
    }

    @Override
    public void setAdapter(ListAdapter adapter) {
        if (adapter != null) {
            adapter.registerDataSetObserver(new DataSetObserver() {
                @Override
                public void onChanged() {
                    // 延时调用加载完毕后的处理方法，保证数据已经展示咱界面
                    Handler handler = new Handler();
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            onRefreshComplete();
                            onLoadMoreComplete();
                        }
                    }, 10);
                    super.onChanged();
                }
            });
        }
        super.setAdapter(adapter);
    }

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
        ListAdapter adapter = getAdapter();
        if (adapter != null) {
            if (adapter.getCount() - 1 == getLastVisiblePosition()) {
                isFootBarWork = true;
            } else {
                isFootBarWork = false;
            }
        }
        if (isFootBarWork && mAutoLoadEnabled && mCanLoadMore) {
            startLoadMore();
        }
    }


    @Override
    public void setPadding(int left, int top, int right, int bottom) {
        super.setPadding(left, top, right, bottom);
        mPaddingTop = top;
        mPaddingBottom = bottom;
    }

    /**
     * 开始加载更多
     */
    private void startLoadMore() {
        mPullRefreshState = REFRESHING;
        changeFooterViewByState();
        onPullToLoadMore();
    }

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem,
                         int visibleItemCount, int totalItemCount) {
        /* 获取当前所能看到的项目序号 以及总条数 */
        firstItemIndex = firstVisibleItem;
        totalSize = totalItemCount;
        if (getLastVisiblePosition() == totalItemCount - 1) {
            isFootBarWork = true;
        } else {
            isFootBarWork = false;
        }
        mNeedShowNoData = totalItemCount > visibleItemCount;
    }


    /**
     * 重写onTouchEvent 处理ListView的上拉和下拉事件
     *
     * @param ev
     * 触摸事件
     * @return 触摸事件是否被消耗掉
     */

    long startTime;

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // 手势落在屏幕上，记录起始位置
                if (!isRecord) {
                    isRecord = true;
                    startY = (int) ev.getY();
                }
                startTime = System.currentTimeMillis();
                break;
            case MotionEvent.ACTION_MOVE:
                // 手势移动，根据手势判断是下拉还是上拉
                int tempY = (int) ev.getY();
                if (!isRecord && (firstItemIndex == 0 || isFootBarWork)) {
                    isRecord = true;
                    startY = tempY;
                }
                if (tempY - startY > 0 && firstItemIndex == 0 && isInitRefresh) {
                    // 下拉动作
                    pullToRefresh(tempY);
                } else if (tempY - startY < 0 && isFootBarWork && isInitLoadMore && mCanLoadMore) {
                    // 上拉动作
                    pullToLoadMore(tempY);
                }
                if (tempY - startY == 0) {
                    // 交替动作界限 将标志位重置
                    mPullRefreshState = DONE;
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
                int upY = (int) ev.getY();
                if (isInitRefresh) {
                    // 手势抬起，执行对应的操作
                    if (mPullRefreshState != REFRESHING) {
                        // 未处于正在加载状态中
                        if (mPullRefreshState == DONE) {

                        }
                        if (mPullRefreshState == PULL_TO_REFRESH) {
                            // 下拉刷新界限内
                            mPullRefreshState = DONE;
                            changeHeaderViewByState();
                        }
                        if (mPullRefreshState == RELEASE_TO_REFRESH) {
                            // 触发刷新
                            mPullRefreshState = REFRESHING;
                            changeHeaderViewByState();
                            onPullToRefresh();
                        }
                    }
                }
                if (isInitLoadMore && isFootBarWork && mCanLoadMore) {
                    System.out.println("mPullRefreshState-----------------"
                            + mPullRefreshState);
                    if (mPullRefreshState != RELEASE_RAISE_TO_REFRESH
                            || mPullRefreshState != RELEASE_TO_REFRESH) {
                        // 上拉刷新
                        if (-1 * (upY - startY) < mFooterHeight * raiseBoundary) {
                            // 界限内
                            mPullRefreshState = DONE;
                        } else {
                            // 触发上拉加载
                            mPullRefreshState = REFRESHING;
                            onPullToLoadMore();
                        }
                        changeFooterViewByState();
                    }
                    if (mPullRefreshState == RAISE_TO_REFRESH) {
                        // 上拉加载界限内
                        mPullRefreshState = DONE;
                        changeFooterViewByState();
                    }
                }
                isFootBarWork = false;
                isRecord = false;
                isBack = false;
                break;

            default:
                break;
        }
        return super.onTouchEvent(ev);
    }

    /**
     * 初始化下拉提示控件
     */
    private void initHeader() {
        /* 获取子控件 */
        mRefreshView = View.inflate(mContext,
                R.layout.refresh_listview_header_view, null);
        mRefreshTextView = (TextView) mRefreshView
                .findViewById(R.id.head_action_tip);
        mRefreshTimeView = (TextView) mRefreshView
                .findViewById(R.id.head_last_updated);
        mArrowImageView = (ImageView) mRefreshView
                .findViewById(R.id.head_arrowImageView);
        mRefreshProgressBar = (ProgressBar) mRefreshView
                .findViewById(R.id.head_progressBar);
        measureView(mRefreshView);

		/* 获取提示控件高度 */
        mHeaderHeight = mRefreshView.getMeasuredHeight();
        mRefreshView.setPadding(0, -1 * mHeaderHeight, 0, 0);
        mRefreshView.invalidate();
        addHeaderView(mRefreshView, null, false);
        setOnScrollListener(this);

		/* 下拉箭头的旋转动画 */
        animation = new RotateAnimation(0, -180,
                RotateAnimation.RELATIVE_TO_SELF, 0.5f,
                RotateAnimation.RELATIVE_TO_SELF, 0.5f);
        animation.setInterpolator(new LinearInterpolator());
        animation.setDuration(250);
        animation.setFillAfter(true);
        reverseAnimation = new RotateAnimation(-180, 0,
                RotateAnimation.RELATIVE_TO_SELF, 0.5f,
                RotateAnimation.RELATIVE_TO_SELF, 0.5f);
        reverseAnimation.setInterpolator(new LinearInterpolator());
        reverseAnimation.setDuration(200);
        reverseAnimation.setFillAfter(true);
        mPullRefreshState = DONE;
    }

    /**
     * 初始化底部信息
     */
    private void initFooter() {
        /* 获取底部“加载更多”控件 */
        isInitLoadMore = true;
        mLoadMoreView = View.inflate(mContext,
                R.layout.refresh_listview_footer_view, null);
        mLoadMoreTextView = (TextView) mLoadMoreView.findViewById(R.id.footerMsg);
        mNoDataTextView = (TextView) mLoadMoreView.findViewById(R.id.no_data_msg);
        mLoadMoreProgressView = mLoadMoreView.findViewById(R.id.footerProgress);
        measureView(mLoadMoreView);
        mFooterHeight = mLoadMoreView.getMeasuredHeight();
        mLoadMoreView.invalidate();
        addFooterView(mLoadMoreView);
        mLoadMoreView.setOnClickListener(mFootLoadMoreListener);
    }

    /**
     * 测量view的的子控件 计算view及其子控件的显示尺寸
     *
     * @param view 需要计算的view控件
     */
    @SuppressWarnings("deprecation")
    private void measureView(View view) {
        ViewGroup.LayoutParams p = view.getLayoutParams();
        if (p == null) {
            p = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        int childWidthSpec = ViewGroup.getChildMeasureSpec(0, 0 + 0, p.width);
        int lpHeight = p.height;
        int childHeightSpec;
        if (lpHeight > 0) {
            childHeightSpec = MeasureSpec.makeMeasureSpec(lpHeight,
                    MeasureSpec.EXACTLY);
        } else {
            childHeightSpec = MeasureSpec.makeMeasureSpec(0,
                    MeasureSpec.UNSPECIFIED);
        }
        view.measure(childWidthSpec, childHeightSpec);
    }

    /**
     * 上拉加载
     *
     * @param tempY 触摸时的Y坐标
     */
    private void pullToLoadMore(int tempY) {
        if (mPullRefreshState != REFRESHING && isRecord) {
            if (mPullRefreshState == DONE) {
                // 开始拖动
                mPullRefreshState = RAISE_TO_REFRESH;
                changeFooterViewByState();
            }
            if (mPullRefreshState == RAISE_TO_REFRESH
                    || mPullRefreshState == RAISE_TO_REFRESH) {
                // 拖动过程中
                setSelection(totalSize - 1);
                if (-1 * ((tempY - startY) / RATIO) >= mFooterHeight
                        * raiseBoundary) {
                    mPullRefreshState = RELEASE_RAISE_TO_REFRESH;
                } else {
                    mPullRefreshState = RAISE_TO_REFRESH;
                    changeFooterViewByState();
                }
            }
			/* 设置Padding值来展示上拉动作 */
            if (firstItemIndex == 0) {
                super.setPadding(0, mPaddingTop + (tempY - startY) / RATIO, 0, mPaddingBottom +
                        -1 * (tempY - startY) / RATIO);
            }
            mLoadMoreView.setPadding(0, 0, 0, -1 * (tempY - startY) / RATIO);
        }
    }

    /**
     * 根据状态更改底部的提示信息
     */
    private void changeFooterViewByState() {
        if (!mCanLoadMore) {
            return;
        }
        switch (mPullRefreshState) {
            case RAISE_TO_REFRESH:
                // 未达临界
                mLoadMoreTextView.setVisibility(VISIBLE);
                mLoadMoreProgressView.setVisibility(GONE);
                break;
            case DONE:
                // 正常状态
                super.setPadding(0, mPaddingTop, 0, mPaddingBottom);
                mLoadMoreView.setPadding(0, 0, 0, 0);
                mLoadMoreTextView.setVisibility(VISIBLE);
                mLoadMoreProgressView.setVisibility(GONE);
                break;
            case RELEASE_RAISE_TO_REFRESH:
                // 超过临界值
                mLoadMoreTextView.setVisibility(VISIBLE);
                mLoadMoreProgressView.setVisibility(GONE);
                break;
            case REFRESHING:
                // 正在刷新中
                super.setPadding(0, mPaddingTop, 0, mPaddingBottom);
                mLoadMoreView.setPadding(0, 0, 0, 0);
                mLoadMoreTextView.setVisibility(GONE);
                mLoadMoreProgressView.setVisibility(VISIBLE);
                break;
            default:
                break;
        }
    }

    /**
     * 下拉刷新
     *
     * @param tempY 触摸时的Y坐标
     */
    private void pullToRefresh(int tempY) {
        // 控件无刷新，后台无更新数据，并且触发了开始记录触摸事件位置
        if (mPullRefreshState != REFRESHING && isRecord) {
            if (mPullRefreshState == RELEASE_TO_REFRESH) {
                // 处于下拉越界状态
                setSelection(0);
                if (((tempY - startY) / RATIO < mHeaderHeight)
                        && (tempY - startY) > 0) {
                    mPullRefreshState = PULL_TO_REFRESH;
                    changeHeaderViewByState();
                } else if (tempY - startY <= 0) {
                    mPullRefreshState = DONE;
                    changeHeaderViewByState();
                }
            }

            if (mPullRefreshState == PULL_TO_REFRESH) {
                // 正常下拉状态
                setSelection(0);
                if ((tempY - startY) / RATIO >= mHeaderHeight) {
                    mPullRefreshState = RELEASE_TO_REFRESH;
                    isBack = true;
                    changeHeaderViewByState();
                } else if (tempY - startY <= 0) {
                    mPullRefreshState = DONE;
                    changeHeaderViewByState();
                }
            }
            // 正常状态
            if (mPullRefreshState == DONE) {
                if (tempY - startY > 0) {
                    mPullRefreshState = PULL_TO_REFRESH;
                    changeHeaderViewByState();
                }
            }

			/* 设置Padding值来展示下拉动作 */
            if (mPullRefreshState == PULL_TO_REFRESH) {
                mRefreshView.setPadding(0, -1 * mHeaderHeight + (tempY - startY)
                        / RATIO, 0, 0);
            }
            if (mPullRefreshState == RELEASE_TO_REFRESH) {
                mRefreshView.setPadding(0, (tempY - startY) / RATIO
                        - mHeaderHeight, 0, 0);
            }

        }
    }

    /**
     * 根据状态改变顶端的提示信息
     */
    private void changeHeaderViewByState() {
        mArrowImageView.clearAnimation();
        switch (mPullRefreshState) {
            // 下拉越界
            case RELEASE_TO_REFRESH:
                mArrowImageView.setVisibility(View.VISIBLE);
                mRefreshProgressBar.setVisibility(View.GONE);
                mRefreshTextView.setVisibility(View.VISIBLE);
                if (mArrowAnimationEnabled) {
                    mArrowImageView.startAnimation(animation);
                }
                mRefreshTextView.setText(R.string.release_to_refresh);
                break;

            // 下拉但未越界
            case PULL_TO_REFRESH:
                mRefreshProgressBar.setVisibility(View.GONE);
                mRefreshTextView.setVisibility(View.VISIBLE);
                mArrowImageView.setVisibility(View.VISIBLE);
                if (isBack) {
                    isBack = false;
                    if (mArrowAnimationEnabled) {
                        mArrowImageView.startAnimation(reverseAnimation);
                    }
                }
                mRefreshTextView.setText(R.string.pull_to_refresh);
                break;

            // 到达可刷新临界并松手
            case REFRESHING:
                mRefreshView.setPadding(0, 0, 0, 0);
                mRefreshProgressBar.setVisibility(View.VISIBLE);
                mArrowImageView.setVisibility(View.GONE);
                mRefreshTextView.setText(R.string.loading_very_hard);
                break;

            // 加载完成或无动作
            case DONE:
                mRefreshProgressBar.setVisibility(View.GONE);
                mArrowImageView.setImageResource(R.drawable.listview_loading);
                mRefreshTextView.setText(R.string.loading_finished);
                mRefreshView.setPadding(0, -1 * mHeaderHeight, 0, 0);
                break;
            default:
                break;
        }
    }

    /**
     * 下拉刷新完毕
     */
    public void onRefreshComplete() {
        if (isInitRefresh) {
            mPullRefreshState = DONE;
            changeHeaderViewByState();
        }
    }

    public void setRefreshTime(String time) {
        mRefreshTimeView.setText(getResources().getString(R.string.update_time, time));
    }

    /**
     * 触发刷新
     */
    public void triggerRefresh() {
        mPullRefreshState = REFRESHING;
        changeHeaderViewByState();
        onPullToRefresh();
    }

    /**
     * 上拉加载完毕
     */
    public void onLoadMoreComplete() {
        if (isInitLoadMore) {
            mLoadMoreView.setClickable(true);
            mPullRefreshState = DONE;
            changeFooterViewByState();
        }
    }

    /**
     * 下拉刷新中
     */
    private void onPullToRefresh() {
        if (mOnRefreshLoadListener != null) {
            mOnRefreshLoadListener.onRefresh(this);
        }
    }

    /**
     * 上拉加载中
     */
    private void onPullToLoadMore() {
        mLoadMoreView.setClickable(false);
        if (mOnRefreshLoadListener != null) {
            mOnRefreshLoadListener.onLoadMore(this);
        }
    }

    /**
     * 刷新时的回调接口
     *
     * @author 刘广茂
     */
    public static interface OnRefreshLoadListener {
        /**
         * 下拉刷新
         */
        void onRefresh(ListView v);

        /**
         * 上拉加载
         */
        void onLoadMore(ListView v);
    }

    /**
     * 底部提示栏监听事件
     */
    private OnClickListener mFootLoadMoreListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            // 触发上拉加载
            if (mCanLoadMore) {
                startLoadMore();
            }
        }
    };
}

