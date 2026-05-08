package jp.co.cssservice.intulauncher

/**
 * デバイス内推論エンジンの状態を表すモデルです。
 */
data class ModelEngineStatus(
    /** エンジン名です。 */
    val engineName: String,
    /** 特徴説明です。 */
    val summary: String,
    /** 使用中の更新方針です。 */
    val updateStrategy: String,
)

/**
 * デバイス内推論エンジンの抽象です。
 */
interface OnDeviceModelEngine {
    /**
     * 現在のエンジン状態を返します。
     */
    fun buildStatus(): ModelEngineStatus
}

/**
 * 現行プロトタイプで使用するローカル推論エンジンです。
 */
class HeuristicOnDeviceModelEngine : OnDeviceModelEngine {
    /**
     * ローカル完結の推論状態を返します。
     */
    override fun buildStatus(): ModelEngineStatus {
        return ModelEngineStatus(
            engineName = "Heuristic Edge Engine",
            summary = "利用統計、コンテキスト信号、通知反応を端末内で統合し、外部送信なしで候補を生成します。",
            updateStrategy = "WorkManager による低頻度更新と、画面復帰時の即時計算を併用します。",
        )
    }
}
