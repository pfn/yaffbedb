package yaffbedb

case class UnitIndex(name: String, min: Int, max: Int, id: String)
case class UnitIndexData(min: Int, max: Int, id: String)
case class UnitSkill(rarity: Int, level: Int, tpe: String, id: Int)
case class UnitStrings(
  description: Option[String],
  summon: Option[String],
  evolution: Option[String],
  affinity: Option[String],
  fusion: Option[String])
case class UnitEntry(
  rarity: Int,
  stats: StatInfo,
  lb: Int,
  abilitySlots: Int,
  magicAffinity: MagicAffinity,
  elementResist: ElementResist,
  statusResist: AilmentResist,
  strings: UnitStrings) {
  def canEquip(m: MateriaIndex): Boolean = m.magicType.fold(true) {
    case "White" => magicAffinity.white >= m.rarity
    case "Black" => magicAffinity.black >= m.rarity
    case "Green" => magicAffinity.green >= m.rarity
    case "Blue"  => magicAffinity.blue  >= m.rarity
    case _       => false
  }
}
case class UnitData(
  name: String,
  id: Int,
  job: String,
  sex: String,
  equip: List[Int],
  entries: Map[String,UnitEntry],
  skills: List[UnitSkill])
case class EsperData(entries: List[EsperEntry])
case class EsperEntry(
  stats: EsperStatInfo,
  elementResist: ElementResist,
  statusResist: AilmentResist
)
case class EsperStatInfo(
  hp:   EsperStatRange,
  mp:   EsperStatRange,
  atk:  EsperStatRange,
  defs: EsperStatRange,
  mag:  EsperStatRange,
  spr:  EsperStatRange
)
case class EsperStatRange(min: Int, max: Int) {
  def effectiveMax = max / 100.0
}
case class SkillInfo(
  name: String,
  active: Boolean,
  tpe: String,
  magicType: Option[String],
  mpCost: Int,
  skilleffects: List[SkillEffect],
  effects: List[String])
case class MateriaIndexData(id: String, effects: Option[List[String]], rarity: Int, magicType: Option[String], skilleffects: List[SkillEffect])
case class MateriaIndex(name: String, id: Int, effects: List[String], rarity: Int, magicType: Option[String], skilleffects: List[SkillEffect]) {
  def describeEffects(unit: Option[UnitData]) = {
    SkillEffect.collateEffects(unit, skilleffects).toString
  }
}
case class EquipStats(
  hp: Int, mp: Int, atk: Int, defs: Int, mag: Int, spr: Int,
  elementResists: Option[EquipElementResist],
  statusResists: Option[EquipAilments],
  statusEffects: Option[EquipAilments],
  element: Option[List[String]]) {
  override def toString = {
    ((if (atk != 0) List(s"ATK+${atk}") else Nil) ++
    (if (defs != 0) List(s"DEF+${defs}") else Nil) ++
    (if (mag != 0) List(s"MAG+${mag}") else Nil) ++
    (if (spr != 0) List(s"SPR+${spr}") else Nil) ++
    (if (hp != 0) List(s"HP+${hp}") else Nil) ++
    (if (mp != 0) List(s"MP+${mp}") else Nil)).mkString(" ")
  }

  def elementResist = elementResists.fold(ElementResist.zero)(_.asElementResist)
  def ailmentResist = statusResists.fold(AilmentResist.zero)(_.asAilmentResist)
}
sealed trait EquipReq {
  def canEquip(unit: UnitData): Boolean
}
case class SexEquipReq(sex: Int) extends EquipReq {
  def canEquip(unit: UnitData) = unit.sex == sex
}
case class UnitEquipReq(id: Int) extends EquipReq {
  def canEquip(unit: UnitData) = unit.id == id
}
case class EquipIndexData(
  id: Int, slotId: Int, skills: Option[List[Int]], tpe: Int, skilleffects: List[SkillEffect], effects: Option[List[String]], skillEffects: Map[String,List[String]], stats: EquipStats, req: Option[EquipReq])
case class EquipIndex(
  name: String, id: Int, slotId: Int, skills: List[Int], tpe: Int, skilleffects: List[SkillEffect], effects: List[String], skillEffects: Map[String,List[String]], stats: EquipStats, req: Option[EquipReq]) {
  def describeEffects(unit: Option[UnitData]) = {
    SkillEffect.collateEffects(unit, skilleffects).toString
  }

  def canEquip(unit: Option[UnitData]) =
    unit.fold(true)(u => req.forall(_.canEquip(u)))
}
case class MagicAffinity(white: Int, black: Int, green: Int, blue: Int)
case class StatRange(min: Int, max: Int, pots: Int) {
  def maxpots = max + pots
}
sealed trait SkillEffect {
  def restrictions: Set[Int]
  def canUse(unit: UnitData) = restrictions.isEmpty || restrictions(unit.id)
}
sealed trait NoRestrictions {
  def restrictions = Set.empty[Int]
}
object SkillEffect {
  val SEX = Map(1 -> "Male", 2 -> "Female")
  val TRIBE = Vector(
    0,
    "Beast",
    "Bird",
    "Aquan",
    "Demon",
    "Man",
    "Machine",
    "Dragon",
    "Spirit",
    "Bug",
    "Stone",
    "Plant",
    "Undead"
  )
  val ELEMENTS = Map(
    "Fire"      -> 1,
    "Ice"       -> 2,
    "Lightning" -> 3,
    "Water"     -> 4,
    "Wind"      -> 5,
    "Earth"     -> 6,
    "Light"     -> 7,
    "Dark"      -> 8,
  )

  val EQUIP = Map(
    1 -> "Dagger",
    2 -> "Sword",
    3 -> "Great Sword",
    4 -> "Katana",
    5 -> "Staff",
    6 -> "Rod",
    7 -> "Bow",
    8 -> "Axe",
    9 -> "Hammer",
    10 -> "Spear",
    11 -> "Harp",
    12 -> "Whip",
    13 -> "Throwing",
    14 -> "Gun",
    15 -> "Mace",
    16 -> "Fist",
    30 -> "L Shield",
    31 -> "H Shield",
    40 -> "Hat",
    41 -> "Helm",
    50 -> "Clothes",
    51 -> "L Armor",
    52 -> "H Armor",
    53 -> "Robe",
    60 -> "Accessory"
  )


  def apply(restrict: List[Int], x: Int, y: Int, z: Int, xs: List[Int]): SkillEffect = {
    (x, y, z) match {
      case (0 | 1, 3, 1)     => PassiveStatEffect.decode(restrict.toSet, xs)
      case (0 | 1, 3, 2)     => PassiveStatusResist.decode(restrict.toSet, xs)
      case (0 | 1, 3, 3)     => PassiveElementResist.decode(restrict.toSet, xs)
      case (0 | 1, 3, 5)     => PassiveEquipEffect.decode(xs)
      case (0 | 1, 3, 10004) => PassiveWeapEleStatEffect.decode(xs)
      case (0 | 1, 3, 6)     => PassiveEquipStatEffect.decode(xs)
      case (0 | 1, 3, 11)    => PassiveKillerEffect.decode(xs)
      case (0 | 1, 3, 21)    => PassiveEvoMagEffect.decode(xs)
      case (0 | 1, 3, 22)    => PassiveDodgeEffect.decode(xs)
      case (0 | 1, 3, 54)    => PassiveDodgeEffect.decode(xs)
      case (0 | 1, 3, 17)    => PassiveJumpEffect.decode(xs)
      case (0 | 1, 3, 14)    => PassiveDualWieldEffect.decode(xs)
      case (0 | 1, 3, 13)    => PassiveDoublehandEffect.decode(xs)
      case (0 | 1, 3, 25)    => PassiveCamouflageEffect.decode(restrict.toSet, xs)
      case (0 | 1, 3, 32)    => PassiveRefreshEffect.decode(restrict.toSet, xs)
      case (0 | 1, 3, 31)    => PassiveLimitBurstRateEffect.decode(xs)
      case (0 | 1, 3, 33)    => PassiveLimitBurstFillEffect.decode(xs)
      case _ => UnknownSkillEffect
    }
  }

  // not yet collated camo, refresh, lb rate, lb fill
  case class CollatedEffect(
    elementResists: PassiveElementResist,
    statusResists: PassiveStatusResist,
    stats: PassiveStatEffect,
    killers: Map[Int,(Int,Int)],
    equipStats: Map[Int,PassiveStatEffect],
    weapEleStats: Map[Int,PassiveStatEffect],
    equips: Set[Int],
    evomag: Int,
    dodge: PassiveDodgeEffect,
    jump: Int,
    lbrate: Int,
    lbfill: Int,
    refresh: Int,
    camouflage: Int,
    dh: Int,
    dw: PassiveDualWieldEffect) {

    def canDualWield(weapon: Int) = dw.all || dw.weapons(weapon)
    def canEquip(tpe: Int, info: Option[UnitData]) =
      info.fold(false)(_.equip.toSet(tpe)) || equips(tpe)

    def dwString = {
      if (dw.all) "DualWield"
      else if (dw.weapons.nonEmpty) {
        val weaps = dw.weapons.toList.sorted.map(EQUIP.apply).mkString(",")
        s"""DualWield(${weaps})"""
      } else ""
    }
    def equipStatsString =
      equipStats.keys.map(k => s"(${equipStats(k)} w/ ${EQUIP(k)})").mkString(" ")
    override def toString = {
      dwString + " " + stats.toString + " " +
        equipStatsString
    }
  }
  object CollatedEffect {
    def apply(): CollatedEffect = CollatedEffect(
      PassiveElementResist(Set.empty, 0, 0, 0, 0, 0, 0, 0, 0),
      PassiveStatusResist(Set.empty, 0, 0, 0, 0, 0, 0, 0, 0),
      PassiveStatEffect.zero,
      Map.empty,
      Map.empty,
      Map.empty,
      Set.empty,
      0,
      PassiveDodgeEffect(0, 0),
      0,
      0,
      0,
      0,
      0,
      0,
      PassiveDualWieldEffect(Set.empty, false))
  }

  def collateEffects(unit: Option[UnitData], xs: List[SkillEffect]) = {
    xs.filter(e => unit.forall(e.canUse)).foldLeft(CollatedEffect()) { (a, eff) => eff match {
      case e@PassiveElementResist(_,_, _, _, _, _, _, _, _) =>
        a.copy(elementResists = a.elementResists + e)
      case e@PassiveStatusResist(_,_,_,_,_,_,_,_,_) =>
        a.copy(statusResists = a.statusResists + e)
      case e@PassiveStatEffect(_,_,_,_,_,_,_) =>
        a.copy(stats = a.stats + e)
      case e@PassiveWeapEleStatEffect(_,_,_,_,_,_,_) =>
        val eff = a.weapEleStats.getOrElse(e.element, PassiveStatEffect.zero)
        a.copy(weapEleStats = a.weapEleStats + ((e.element, eff + e.asPassiveStatEffect)))
      case e@PassiveEquipStatEffect(_,_,_,_,_,_,_) =>
        val eff = a.equipStats.getOrElse(e.cond, PassiveStatEffect.zero)
        a.copy(equipStats = a.equipStats +
          ((e.cond, eff + e.asPassiveStatEffect)))
      case PassiveKillerEffect(tribe, phys, mag) =>
        val (p,m) = a.killers.getOrElse(tribe, (0,0))
        a.copy(killers = a.killers + ((tribe, (p + phys, m + mag)))) 
      case PassiveEvoMagEffect(evomag) =>
        a.copy(evomag = a.evomag + evomag)
      case PassiveEquipEffect(equip) =>
        a.copy(equips = a.equips + equip)
      case PassiveDodgeEffect(p, m) =>
        a.copy(dodge = PassiveDodgeEffect(p + a.dodge.phys, m + a.dodge.mag))
      case PassiveJumpEffect(j) =>
        a.copy(jump = a.jump + j)
      case PassiveLimitBurstFillEffect(mod) => a.copy(lbfill = a.lbfill + mod)
      case PassiveLimitBurstRateEffect(mod) => a.copy(lbrate = a.lbrate + mod)
      case PassiveCamouflageEffect(_, mod) => a.copy(camouflage = a.camouflage + mod)
      case PassiveRefreshEffect(_, mod) => a.copy(refresh = a.refresh + mod)
      case PassiveDoublehandEffect(dh) =>
        a.copy(dh = a.dh + dh)
      case PassiveDualWieldEffect(weaps, all) =>
        a.copy(dw = PassiveDualWieldEffect(a.dw.weapons ++ weaps, all || a.dw.all))
      case _ => a
    }}
  }
}
case object UnknownSkillEffect extends SkillEffect {
  override def restrictions = Set.empty
}
object PassiveElementResist {
  def decode(restrict: Set[Int], xs: List[Int]): SkillEffect = xs match {
    case List(a, b, c, d, e, f, g, h) =>
      PassiveElementResist(restrict, a, b, c, d, e, f, g, h)
    case _ => UnknownSkillEffect
  }
}
object PassiveStatusResist {
  def decode(restrict: Set[Int], xs: List[Int]): SkillEffect = xs match {
    case List(a, b, c, d, e, f, g, h) =>
      PassiveStatusResist(restrict, a, b, c, d, e, f, g, h)
    case _ => UnknownSkillEffect
  }
}
case class PassiveElementResist(
  restrictions: Set[Int],
  fire: Int,
  ice: Int,
  lightning: Int,
  water: Int,
  wind: Int,
  earth: Int,
  light: Int,
  dark: Int
) extends SkillEffect {
  def +(o: PassiveElementResist) =
    PassiveElementResist(restrictions.intersect(o.restrictions), fire + o.fire, ice + o.ice, lightning + o.lightning,
      water + o.water, wind + o.wind, earth + o.earth,
      light + o.light, dark + o.dark)
  def asElementResist = ElementResist(fire, ice, lightning, water, wind, earth, light, dark)
}
case class PassiveStatusResist(
  restrictions: Set[Int],
  poison: Int,
  blind: Int,
  sleep: Int,
  silence: Int,
  paralysis: Int,
  confusion: Int,
  disease: Int,
  petrify: Int
) extends SkillEffect {
  def +(o: PassiveStatusResist) = PassiveStatusResist(
    restrictions.intersect(o.restrictions),
    poison + o.poison,
    blind + o.blind,
    sleep + o.sleep,
    silence + o.silence,
    paralysis + o.paralysis,
    confusion + o.confusion,
    disease + o.disease,
    petrify + o.petrify)
  def asAilmentResist = AilmentResist(poison, blind, sleep, silence, paralysis, confusion, disease, petrify)
}
object PassiveWeapEleStatEffect {
  def decode(xs: List[Int]): SkillEffect = xs match {
    case List(a, b, c, d, e, f, g) =>
      PassiveWeapEleStatEffect(a, b, c, d, e, f, g)
    case _ => UnknownSkillEffect
  }
}
case class PassiveWeapEleStatEffect(
  element: Int,
  hp:   Int,
  mp:   Int,
  atk:  Int,
  defs: Int,
  mag:  Int,
  spr:  Int) extends SkillEffect with NoRestrictions {
  def asPassiveStatEffect =
    PassiveStatEffect(Set.empty, hp, mp, atk, defs, mag, spr)
}
object PassiveEquipEffect {
  def decode(xs: List[Int]): SkillEffect = xs match {
    case List(a) => PassiveEquipEffect(a)
    case _ => UnknownSkillEffect
  }
}
case class PassiveEquipEffect(equip: Int) extends SkillEffect with NoRestrictions
object PassiveEquipStatEffect {
  def decode(xs: List[Int]): SkillEffect = xs match {
    case List(a, b, c, d, e) =>
      PassiveEquipStatEffect(a, 0, 0, b, c, d, e)
    case _ => UnknownSkillEffect
  }
}
case class PassiveDoublehandEffect(dh: Int) extends SkillEffect with NoRestrictions
object PassiveDoublehandEffect {
  def decode(xs: List[Int]): SkillEffect = xs match {
    case List(a) => PassiveDoublehandEffect(a)
  }
}
case class PassiveEquipStatEffect(
  cond: Int,
  hp:   Int,
  mp:   Int,
  atk:  Int,
  mag:  Int,
  defs: Int,
  spr:  Int) extends SkillEffect {
  def restrictions = Set.empty
  def asPassiveStatEffect =
    PassiveStatEffect(Set.empty, hp, mp, atk, defs, mag, spr)
}
object PassiveStatEffect {
  def decode(restrict: Set[Int], xs: List[Int]): SkillEffect = xs match {
    case List(a, b, c, d, e, f, g) =>
      PassiveStatEffect(restrict, e, f, a, b, c, d)
    case _ => UnknownSkillEffect
  }

  def zero = PassiveStatEffect(Set.empty, 0, 0, 0, 0, 0, 0)
}
case class PassiveStatEffect(
  restrict: Set[Int],
  hp:   Int,
  mp:   Int,
  atk:  Int,
  defs: Int,
  mag:  Int,
  spr:  Int) extends SkillEffect with NoRestrictions {
  def +(o: PassiveStatEffect) = PassiveStatEffect(restrict.intersect(o.restrict), hp + o.hp, mp + o.mp,
    atk + o.atk, defs + o.defs, mag + o.mag, spr + o.spr)
  override def toString = {
    ((if (atk != 0) List(s"ATK+$atk%") else Nil) ++
    (if (mag != 0) List(s"MAG+$mag%") else Nil) ++
    (if (defs != 0) List(s"DEF+$defs%") else Nil) ++
    (if (spr != 0) List(s"SPR+$spr%") else Nil) ++
    (if (hp != 0) List(s"HP+$hp%") else Nil) ++
    (if (mp != 0) List(s"MP+$mp%") else Nil)).mkString(" ")
  }
}
object PassiveKillerEffect {
  def decode(xs: List[Int]): SkillEffect = xs match {
    case List(a, b, c) =>
      PassiveKillerEffect(a, b, c)
    case _ => UnknownSkillEffect
  }
}
case class PassiveKillerEffect(tribe: Int, phys: Int, mag: Int)
extends SkillEffect with NoRestrictions
object PassiveEvoMagEffect {
  def decode(xs: List[Int]): SkillEffect = xs match {
    case List(a) =>
      PassiveEvoMagEffect(a)
    case _ => UnknownSkillEffect
  }
}
case class PassiveEvoMagEffect(evomag: Int) extends SkillEffect with NoRestrictions
object PassiveDodgeEffect {
  def decode(xs: List[Int]): SkillEffect = xs match {
    case List(a) => PassiveDodgeEffect(a, 0)
    case List(a, b) => PassiveDodgeEffect(0, b)
    case _ => UnknownSkillEffect
  }
}
case class PassiveDodgeEffect(phys: Int, mag: Int) extends SkillEffect with NoRestrictions
object PassiveCamouflageEffect {
  def decode(restrict: Set[Int], xs: List[Int]): SkillEffect = xs match {
    case List(a) => PassiveCamouflageEffect(restrict, a)
    case _ => UnknownSkillEffect
  }
}
case class PassiveCamouflageEffect(restrictions: Set[Int], camo: Int) extends SkillEffect
object PassiveRefreshEffect {
  def decode(restrict: Set[Int], xs: List[Int]): SkillEffect = xs match {
    case List(a) => PassiveRefreshEffect(restrict, a)
    case _ => UnknownSkillEffect
  }
}
case class PassiveRefreshEffect(restrictions: Set[Int], refresh: Int) extends SkillEffect
object PassiveJumpEffect {
  def decode(xs: List[Int]): SkillEffect = xs match {
    case List(a) => PassiveJumpEffect(a)
    case _ => UnknownSkillEffect
  }
}
case class PassiveJumpEffect(mod: Int) extends SkillEffect with NoRestrictions
object PassiveLimitBurstRateEffect {
  def decode(xs: List[Int]): SkillEffect = xs match {
    case List(a) => PassiveLimitBurstRateEffect(a)
    case _ => UnknownSkillEffect
  }
}
object PassiveLimitBurstFillEffect {
  def decode(xs: List[Int]): SkillEffect = xs match {
    case List(a) => PassiveLimitBurstFillEffect(a)
    case _ => UnknownSkillEffect
  }
}
case class PassiveLimitBurstFillEffect(mod: Int) extends SkillEffect with NoRestrictions
case class PassiveLimitBurstRateEffect(mod: Int) extends SkillEffect with NoRestrictions
object PassiveDualWieldEffect {
  def decode(xs: List[Int]): SkillEffect =
    if (xs.isEmpty) PassiveDualWieldEffect(Set.empty, true)
    else PassiveDualWieldEffect(xs.toSet, false)
}
case class PassiveDualWieldEffect(weapons: Set[Int], all: Boolean) extends SkillEffect with NoRestrictions

case class StatInfo(hp: StatRange,
  mp: StatRange,
  atk: StatRange,
  mag: StatRange,
  defs: StatRange,
  spr: StatRange)
case class EquipAilments(
  poison: Option[Int],
  blind: Option[Int],
  sleep: Option[Int],
  silence: Option[Int],
  paralysis: Option[Int],
  confusion: Option[Int],
  disease: Option[Int],
  petrify: Option[Int]) {
  def asAilmentResist = AilmentResist(poison.getOrElse(0),
    blind.getOrElse(0),
    sleep.getOrElse(0),
    silence.getOrElse(0),
    paralysis.getOrElse(0),
    confusion.getOrElse(0),
    disease.getOrElse(0),
    petrify.getOrElse(0))
}

case class AilmentResist(
  poison: Int,
  blind: Int,
  sleep: Int,
  silence: Int,
  paralysis: Int,
  confusion: Int,
  disease: Int,
  petrify: Int) {
  def +(o: AilmentResist) = AilmentResist(poison + o.poison, blind + o.blind, sleep + o.sleep, silence + o.silence, paralysis + o.paralysis, confusion + o.confusion, disease + o.disease, petrify + o.petrify)
}
object AilmentResist {
  def zero = AilmentResist(0, 0, 0, 0, 0, 0, 0, 0)
}
case class EquipElementResist(
  fire: Option[Int],
  ice: Option[Int],
  lightning: Option[Int],
  water: Option[Int],
  wind: Option[Int],
  earth: Option[Int],
  light: Option[Int],
  dark: Option[Int]) {
  def asElementResist = ElementResist(fire.getOrElse(0),
    ice.getOrElse(0),
    lightning.getOrElse(0),
    water.getOrElse(0),
    wind.getOrElse(0),
    earth.getOrElse(0),
    light.getOrElse(0),
    dark.getOrElse(0))
}
case class ElementResist(
  fire: Int,
  ice: Int,
  lightning: Int,
  water: Int,
  wind: Int,
  earth: Int,
  light: Int,
  dark: Int) {
  def +(o: ElementResist) = ElementResist(fire + o.fire, ice + o.ice, lightning + o.lightning, water + o.water, wind + o.wind, earth + o.earth, light + o.light, dark + o.dark)

}
object ElementResist {
  def zero = ElementResist(0, 0, 0, 0, 0, 0, 0, 0)
}
