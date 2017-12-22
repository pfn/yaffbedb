package yaffbedb

import scalajs.js.annotation.JSExportTopLevel
import org.scalajs.dom.document
import org.scalajs.dom.window

import outwatch.dom._
import rxscalajs.{Observable,Subject}
import rxscalajs.subjects.{BehaviorSubject,ReplaySubject}
import boopickle.Default._
import scala.concurrent.duration.{span => _, _}

object YaFFBEDB {

  case class PageState(
    unit:  Option[Int],
    rhand: Option[Int],
    lhand: Option[Int],
    head:  Option[Int],
    body:  Option[Int],
    acc1:  Option[Int],
    acc2:  Option[Int],
    mat1:  Option[Int],
    mat2:  Option[Int],
    mat3:  Option[Int],
    mat4:  Option[Int],
    pots:  Pots,
    enhs:  Map[Int,Int],
    esper: Option[Int],
    esperRarity: Int,
    esperSkills: Map[Int,Boolean]
  ) {
    override def toString = {
      val ue = unit.map { u =>
        esper.fold(u.toString)(esp => u.toString + "," + esp + "," + esperRarity)
      }

      val mats =
        List(mat1, mat2, mat3, mat4).map(_.fold("")(_.toString)).mkString(",")
      val eqs =
        List(rhand, lhand, head, body, acc1, acc2).map(_.fold("")(_.toString)).mkString(",")
      val enh = enhs.toList.map(kv => kv._1 + "=" + kv._2).mkString(",")

      ue.fold("")(_ => List(ue.getOrElse(""), mats, eqs, enh).mkString("/"))
    }
  }
  object PageState {
    import util.Try
    def strId(s: Option[String]): Option[Int] =
      s.flatMap(x => Try(x.toInt).toOption)
    def or0(s: Option[String]): Int = strId(s).getOrElse(0)
    def idOfEq(e: (Option[EquipIndex],Double)) = e._1.map(_.id)
    def idOfMat(e: (Option[MateriaIndex],Double)) = e._1.map(_.id)
    def empty = PageState(None,
      None, None, None, None, None, None,
      None, None, None, None,
      Pots.none, Map.empty, None, 1, Map.empty)
    def from(s: String): PageState = {
      val parts = s.split("/").map(_.split(",")).zipWithIndex
      parts.foldLeft(PageState.empty) { case (ac,(ps,i)) =>
        if (i == 0) {
          val u = strId(ps.headOption)
          val e = strId(ps.drop(1).headOption)
          val r = strId(ps.drop(2).headOption)
          ac.copy(unit = u, esper = e, esperRarity = r.getOrElse(1))
        } else if (i == 1) {
          ac.copy(mat1 = strId(ps.headOption),
            mat2 = strId(ps.drop(1).headOption),
            mat3 = strId(ps.drop(2).headOption),
            mat4 = strId(ps.drop(3).headOption))
        } else if (i == 2) {
          ac.copy(rhand = strId(ps.headOption),
            lhand = strId(ps.drop(1).headOption),
            head = strId(ps.drop(2).headOption),
            body = strId(ps.drop(3).headOption),
            acc1 = strId(ps.drop(4).headOption),
            acc2 = strId(ps.drop(5).headOption))
        } else if (i == 3) {
          val es = ps.map(_.split("=")).foldLeft(Map.empty[Int,Int]) { case (ac,xs) =>
            ac + ((xs(0).toInt,xs(1).toInt))
          }
          ac.copy(enhs = es)
        } else
          ac
      }
    }
    def from(unitId: Option[Int], stats: Option[BaseStats],
      eqs: Equipped, abis: Abilities, enhs: Map[Int,Int],
      esper: Option[Int], esperR: Int,
      esperSkills: Map[Int,Boolean]) = PageState(
      unitId,
      idOfEq(eqs.rhand),      idOfEq(eqs.lhand),
      idOfEq(eqs.head),       idOfEq(eqs.body),
      idOfEq(eqs.acc1),       idOfEq(eqs.acc2),
      idOfMat(abis.ability1), idOfMat(abis.ability2),
      idOfMat(abis.ability3), idOfMat(abis.ability4),
      stats.fold(Pots.none)(_.pots), enhs,
      esper, esperR,
      esperSkills)
  }
  @JSExportTopLevel("yaffbedb.YaFFBEDB.main")
  def main(args: Array[String]): Unit = {
    val unitIdSubject = BehaviorSubject[Option[String]](None)
    val unitIdSink = createIdHandler(None)
    val unitId = unitIdSubject.merge(unitIdSink).distinctUntilChanged

    val unitIndex = Data.get[List[UnitIndex]]("pickle/unit/index.pickle").combineLatest(unitId.startWith(None)).map { case (us, id) =>
      List(option(value := EMPTY, "-- Select a unit --")) ++
        us.map { u =>
          val rarity = ("\u2605" * u.min) + ("\u2606" * (u.max - u.min))
          option(value := u.id, selected := id.exists(_ == u.id), s"${u.name}: $rarity")
        }
    }.publishReplay(1).refCount
    val pots = PotSubjects()

    val equips  = Data.get[List[EquipIndex]]("pickle/equip/index.pickle")
    val materia = Data.get[List[MateriaIndex]]("pickle/materia/index.pickle")
    val espers  = Data.get[Map[String,Int]]("pickle/esper/index.pickle")

    val unitInfo: Observable[Option[UnitData]] = unitId.flatMap(id => 
      id.fold(Observable.just(Option.empty[UnitData])) { id_ =>
        Data.get[UnitData](s"pickle/unit/$id_.pickle").map { u =>
          Some(u)
        }
      }).publishReplay(1).refCount
    val enhancements: Observable[Map[String,Enhancement]] = unitId.flatMap(id =>
      id.fold(Observable.just(Map.empty[String,Enhancement])) { id_ =>
        Data.get[Map[String,Enhancement]](s"pickle/enhance/$id_.pickle").catchError(_ => Observable.just(Map.empty))
      })
    val enhancedSkills: Observable[Map[Int,SkillInfo]] = enhancements.flatMap { es =>
      Observable.combineLatest(es.toList.map { case (k,v) =>
        Data.get[SkillInfo](s"pickle/skill/${v.newSkill}.pickle").map(d => (v.oldSkill,d))
      }).map(_.toMap)
    }

    val unitEntry: Observable[Option[UnitEntry]] = unitInfo.map {
      _.fold(Option.empty[UnitEntry])(
        _.entries.values.toList.sortBy(_.rarity).lastOption)
    }.publishReplay(1).refCount

    val unitSkills = unitInfo.flatMap { u =>
      Observable.combineLatest(u.fold(
        List.empty[Observable[(UnitSkill, SkillInfo)]])(_.skills.map { s =>
        Data.get[SkillInfo](s"pickle/skill/${s.id}.pickle").map { s -> _ }
      }))
    }

    val esperIdSubject = BehaviorSubject[Option[String]](None)
    val esperRaritySubject = ReplaySubject.withSize[Int](1)
    val esperStats = createHandler[Option[EsperStatInfo]](None)
    val esperSkills = createHandler[List[SkillInfo]](Nil)
    val esper = createHandler[Option[EsperData]](None)
    val esperEntry = createHandler[Option[EsperEntry]](None)

    def equipFor(idOb: Observable[Option[String]]): Observable[Option[EquipIndex]] = for {
      ms <- equips
      id <- idOb
    } yield {
      ms.find(_.id == id.flatMap(i => util.Try(i.toInt).toOption).getOrElse(0))
    }
    val rhandId = createIdHandler(None)
    val rhandSubject = ReplaySubject.withSize[Option[String]](1)
    val rhand = equipFor(rhandId.merge(rhandSubject).distinctUntilChanged)
    val lhandId = createIdHandler(None)
    val lhandSubject = ReplaySubject.withSize[Option[String]](1)
    val lhand = equipFor(lhandId.merge(lhandSubject).distinctUntilChanged)
    val headId = createIdHandler(None)
    val headSubject = ReplaySubject.withSize[Option[String]](1)
    val headEquip = equipFor(headId.merge(headSubject).distinctUntilChanged)
    val bodyId = createIdHandler(None)
    val bodySubject = ReplaySubject.withSize[Option[String]](1)
    val bodyEquip = equipFor(bodyId.merge(bodySubject).distinctUntilChanged)
    val acc1Id = createIdHandler(None)
    val acc1Subject = ReplaySubject.withSize[Option[String]](1)
    val acc1 = equipFor(acc1Id.merge(acc1Subject).distinctUntilChanged)
    val acc2Id = createIdHandler(None)
    val acc2Subject = ReplaySubject.withSize[Option[String]](1)
    val acc2 = equipFor(acc2Id.merge(acc2Subject).distinctUntilChanged)

    val equippedGear = withStamp(rhand).combineLatest(
      withStamp(lhand), withStamp(headEquip), withStamp(bodyEquip))
    val accs = withStamp(acc1).combineLatest(withStamp(acc2))

    val unitStats = createHandler[Option[BaseStats]](None)
    val selectedTraits = createHandler[List[SkillInfo]]()
    val unitPassives = selectedTraits.map(_.flatMap(_.passives)).publishReplay(1).refCount
    val _sorting = createHandler[Sort](Sort.AZ)
    val sorting = _sorting.publishReplay(1).refCount
    val abilitySubjects = AbilitySubjects()
    val abilityValidatorSubjects = AbilitySubjects()
    val (ability1, ability2, ability3, ability4, abilitySlots) =
      components.abilitySlots(materia, unitInfo, unitPassives, unitEntry, sorting, abilitySubjects, abilityValidatorSubjects)
    val abilities = withStamp(ability1).combineLatest(
      withStamp(ability2), withStamp(ability3),
      withStamp(ability4)).map(Abilities.tupled.apply)

    abilities { ab =>
      ab.validateUnique(abilitySubjects, abilityValidatorSubjects)
    }

    val equipped = equippedGear.combineLatest(accs).map { a =>
      Equipped.tupled.apply(a._1 + a._2)
    }.combineLatest(abilities).distinctUntilChanged

    def passivesFromAll(equips: List[EquipIndex],
      abilities: List[MateriaIndex]) : List[SkillEffect] = {
      passivesFrom(equips ++ abilities)
    }
    def skillsFromAll(equips: List[EquipIndex],
      abilities: List[MateriaIndex]): List[(String,String)] = {
      skillsFrom(equips ++ abilities).map(i => i.name -> i.effects.mkString("\n"))
    }

    def passivesFrom(equip: List[SkillIndex]) = for {
      es <- equip
      is <- es.skillInfo
      ps <- is.passives
    } yield ps
    def skillsFrom(equip: List[SkillIndex]): List[IndexSkillInfo] =
      equip.flatMap(_.skillInfo)

    def is2h(eqItem: Option[EquipIndex]): Boolean =
      eqItem.exists(_.twohands)
    def typeOf(eqItem: Option[EquipIndex]): Int =
      eqItem.fold(-1)(_.tpe)
    def isSlot(slot: Int, eqItem: Option[EquipIndex]): Boolean =
      eqItem.exists(_.slotId == slot)

    val allPassives = unitInfo.combineLatest(unitPassives, equipped, esperSkills).map {
      case (info, passives,(eqs,abis), fromEsper) =>
      info -> SkillEffect.collateEffects(info, passivesFromAll(eqs.allEquipped, abis.allEquipped) ++ passives ++ fromEsper.flatMap(_.passives))
    }

    val equipSkills: Observable[List[(String,String)]] = equipped.combineLatest(esperSkills).map {
      case ((eqs, abis), fromE) =>
      skillsFromAll(eqs.allEquipped, abis.allEquipped) ++
        fromE.map { e => e.name -> e.effects.mkString("\n") }
    }

    def publishTo[A](sink: Subject[A], value: A): Unit = sink.next(value)

    def handValidator(
      r: Option[EquipIndex], l: Option[EquipIndex],
      info: Option[UnitData], effs: SkillEffect.CollatedEffect,
      sink: Subject[Option[String]], older: Boolean): String = {
      if (isSlot(2, r) && isSlot(2, l) && older) {
        publishTo(sink, None)
        EMPTY
      } else if ((is2h(r) || is2h(l)) && older) {
        publishTo(sink, None)
        EMPTY
      } else if ((isSlot(1, r) && isSlot(1, l)) && info.nonEmpty && !effs.isEmpty &&
        ((!effs.canDualWield(typeOf(r)) || !effs.canDualWield(typeOf(l))) && older)) {
        publishTo(sink, None)
        EMPTY
      } else if (r.nonEmpty && info.nonEmpty && !effs.canEquip(typeOf(r), info)) {
        publishTo(sink, None)
        EMPTY
      } else r.fold(EMPTY)(_.id.toString)
    }

    def equipValidator(
      e: Option[EquipIndex],
      info: Option[UnitData],
      effs: SkillEffect.CollatedEffect, sink: Subject[Option[String]]): String = {
      if (e.nonEmpty && info.nonEmpty && !effs.canEquip(typeOf(e), info)) {
        publishTo(sink, None)
        EMPTY
      } else e.fold(EMPTY)(_.id.toString)
    }

    val rhandValidator = allPassives.combineLatest(equipped).distinctUntilChanged.map {
      case (((info,effs),(eqs, abis))) =>
        handValidator(eqs.rhand._1, eqs.lhand._1, info, effs, rhandSubject, eqs.rhand._2 < eqs.lhand._2)
    }
    val lhandValidator = allPassives.combineLatest(equipped).distinctUntilChanged.map {
      case (((info,effs),(eqs, abis))) =>
        handValidator(eqs.lhand._1, eqs.rhand._1, info, effs, lhandSubject, eqs.lhand._2 < eqs.rhand._2)
    }
    def equipsValidator(sink: Subject[Option[String]], f: Equipped => EqStamp) = allPassives.combineLatest(equipped).distinctUntilChanged.map {
      case (((info,effs),(eqs, abis))) =>
        equipValidator(f(eqs)._1, info, effs, sink)
    }

    val enhSink = createHandler[(Int,Int)]()
    val enhSubject = Subject[(Int,Int)]()
    val enhMap = enhSink.merge(enhSubject).scan(Map.empty[Int,Int]) { (ac, e) =>
      ac + e
    }.distinctUntilChanged.startWith(Map.empty).publishReplay(1).refCount
    def hasEnh(base: SkillInfo, target: SkillInfo) =
      selected <-- enhMap.map { es =>
        es.getOrElse(base.id, base.id) == target.id
      }

    def enhancedInfo[A](info: SkillInfo, enhanced: Option[Int], enhs: Map[Int,SkillInfo], f: SkillInfo => A): A = {
      enhanced.fold(f(info)) { en =>
        val d = enhs.getOrElse(info.id, info)
        val s = if (en == info.id) Some(info)
          else if (d.id == en) Some(d)
          else enhs.get(enhs.getOrElse(info.id, info).id)
        f(s.getOrElse(info))
      }
    }
    def deco[A,B,C](f: (A,B,C) => VNode): ((A,B,C)) => VNode = f.tupled(_)
    val activesTable = {

      unitSkills.combineLatest(enhancedSkills).map( a =>
        a._1.filter(_._2.active).map(b => (b._1, b._2, a._2))).map { ss =>
        components.dataTable(ss,
          "skills-active",
          List("Rarity", "Level", "Name", "Description", "MP"),
          List("unit-skill-rarity", "unit-skill-level",
            "unit-skill-name", "unit-skill-desc", "unit-skill-cost"))(
          List(
            a => div(img(src := s"http://exviusdb.com/static/img/assets/ability/${a._2.icon}"), span(s"${a._1.rarity}\u2605")),
            a => span(a._1.level.toString),
            deco { (us, info, enhs) =>
              enhancementsOf(info.id, enhs).fold {
                span(info.name)
              } { enh =>
                select(inputString(i => info.id -> i.toInt) --> enhSink,
                  option(value := info.id, info.name, hasEnh(info, info)),
                  option(value := enh._1.id, "+1 " + info.name, hasEnh(info, enh._1)),
                  option(value := enh._2.id, "+2 " + info.name, hasEnh(info, enh._2))
                )
              }
            },
            deco { (us, info, enhs) =>
              div(children <-- enhMap.map(e => enhancedInfo(info, e.get(info.id), enhs, _.effects.map(e => div(e)))))
            },
            deco { (us, info, enhs) =>
              span(child <-- enhMap.map(e => enhancedInfo(info, e.get(info.id), enhs, _.mpCost.toString)))
            }
          )
        )
      }
    }

    val traitsTable = {
      // a double-subscription occurs below (???) --> workaround  :-(
      var subscription = Option.empty[rxscalajs.subscription.AnonymousSubscription]

      unitSkills.combineLatest(enhancedSkills).map(a =>
        a._1.filterNot(_._2.active).map{b =>
        (b._1, b._2, a._2)}).map { ss =>
        val infos = ss.map(_._2).toList
        val enhs = ss.headOption.fold(Map.empty[Int,SkillInfo])(_._3)

        subscription.foreach(_.unsubscribe())
        subscription = Some(selectedTraits <-- enhMap.map { es =>
          infos.flatMap { i =>
            val rid = es.getOrElse(i.id, i.id)
            if (rid == i.id || enhs.isEmpty) List(i)
            else enhs.get(i.id).flatMap { s =>
              if (s.id == rid) Some(s) else enhs.get(s.id)
            }.orElse(enhs.get(rid)).toList
          }
        })
        components.dataTable(ss,
          "skills-trait",
          List("Rarity", "Level", "Name", "Description"),
          List("unit-trait-rarity", "unit-trait-level",
            "unit-trait-name", "unit-trait-desc"))(List(
            a => div(img(src := s"http://exviusdb.com/static/img/assets/ability/${a._2.icon}"), span(s"${a._1.rarity}\u2605")),
            a => span(a._1.level.toString),
            deco { (us, info, enhs) =>
              enhancementsOf(info.id, enhs).fold {
                span(info.name)
              } { enh =>
                select(inputString(i => info.id -> i.toInt) --> enhSink,
                  option(value := info.id, info.name, hasEnh(info, info)),
                  option(value := enh._1.id, "+1 " + info.name, hasEnh(info, enh._1)),
                  option(value := enh._2.id, "+2 " + info.name, hasEnh(info, enh._2))
                )
              }
            },
            deco { (us, info, enhs) =>
              div(children <-- enhMap.map(e => enhancedInfo(info, e.get(info.id), enhs, _.effects.map(e => div(e)))))
            },
          )
        )
      }
    }

    val equippedTable = equipSkills.map(es => components.dataTable(es,
      "skills-equip",
      List("Name", "Description"),
      List("unit-equip-name", "unit-equip-desc"))(List(
        //a => div((img(src := s"http://exviusdb.com/static/img/assets/ability/${a._2.icon}") +: a._1.split("\n").map(e => div(e)):_*)),
        a => div(a._1.split("\n").map(e => div(e)):_*),
        a => div(a._2.split("\n").map(e => div(e)):_*)
      ))
    )

    val unitDescription = unitInfo.map { i =>
      i.fold("")(_.entries.values.toList.sortBy(
        _.rarity).lastOption.fold("Unknown")(_.strings.description.headOption.flatten.getOrElse("Unknown")))
    }

    def effectiveStats(u: UnitData, base: BaseStats, equip: EquipIndex, pasv: SkillEffect.CollatedEffect): Stats = {
      val eqs = equip.stats
      val elements = eqs.element.map(e =>
        SkillEffect.ELEMENTS.getOrElse(e, -1))
      val elestats = elements.map(e =>
        pasv.weapEleStats.getOrElse(e, PassiveStatEffect.zero))
      val eqstats = pasv.equipStats.getOrElse(equip.tpe, PassiveStatEffect.zero)
      val s = Stats.fromEquipStats(equip.stats)
      val innates = SkillEffect.collateEffects(Some(u), equip.skillInfo.flatMap(_.passives))

      val innatestats = innates.stats :: innates.equipStats.keys.toList.flatMap {
        k => if (pasv.canEquip(k, Some(u))) List(innates.equipStats(k)) else Nil
      }
      (eqstats :: (innatestats ++ elestats)).foldLeft(s) { (ac, x) =>
        ac + base.asStats * x - base.asStats
      }
    }
    def sortFor(xs: List[EquipIndex], sorting: Sort, pasv: SkillEffect.CollatedEffect, unit: Option[UnitData], base: Option[BaseStats]) = {
      val m = for {
        u <- unit
        b <- base
      } yield {
        val es = effectiveStats(u, b, _: EquipIndex, pasv)
        def cmp(f: Stats => Int):
          (EquipIndex,EquipIndex) => Boolean = (x,y) => f(es(x)) > f(es(y))
        val f: (EquipIndex,EquipIndex) => Boolean = sorting match {
          case Sort.AZ  => (_,_) => true
          case Sort.HP  => cmp(_.hp)
          case Sort.MP  => cmp(_.mp)
          case Sort.ATK => cmp(_.atk)
          case Sort.DEF => cmp(_.defs)
          case Sort.MAG => cmp(_.mag)
          case Sort.SPR => cmp(_.spr)
        }

        if (sorting == Sort.AZ) xs else xs.sortWith(f)
      }
      m.getOrElse(xs)
    }

    def equippable(slots: Set[Int], worn: Observable[Option[EquipIndex]]) = for {
      ((es, (u, passives), sort, base), w) <- equips.combineLatest(allPassives, sorting, unitStats).combineLatest(worn).distinctUntilChanged
    } yield {
      val eqs = es.filter(e =>
        slots(e.slotId) && e.canEquip(u) && passives.canEquip(e.tpe, u))

      List(option(value := EMPTY, "Empty")) ++
        sortFor(eqs, sort, passives, u, base).map { e =>
          val is2h = if (e.twohands) "(2h)" else ""
          option(value := e.id,
            selected := w.exists(_.id == e.id),
            s"${e.name} \u27a1 $is2h ${e.stats} ${e.describeEffects(u)}")
        }
    }

    def eqslot(name: String, slots: Set[Int], validator: Observable[String], worn: Observable[Option[EquipIndex]], sink: outwatch.Sink[Option[String]]): VNode = {
      td(label(name, select(cls := "equip-slot",
        value <-- validator,
        children <-- equippable(slots, worn),
        inputId --> sink)))
    }

    val esperTraining = createHandler[Map[Int,Boolean]](Map.empty)
    val esperTrainingSubject = BehaviorSubject[Map[Int,Boolean]](Map.empty)
    val esperRaritySink = createStringHandler()
    val esperRarity = esperRaritySubject.map(_.toString).merge(esperRaritySink).startWith("1")

    val pageState: Observable[PageState] = equipped.combineLatest(unitStats,unitInfo).combineLatest(espers, esper).combineLatest(esperRarity, esperTraining, enhMap).map {
      case (((((eqs,abis),sts,i),es, e)),rarity, training, enhs) =>
        PageState.from(
          i.map(_.id), sts, eqs, abis, enhs, e.map(x => es(x.names.head)),
          util.Try(rarity.toInt).getOrElse(1), training)
    }.publishReplay(1).refCount

    def subscribeChanges = pageState.bufferTime(1.second).map(_.lastOption) {
      case Some(ps) =>
        if (ps.unit.nonEmpty) {
          val pstr = ps.toString
          if (pstr.isEmpty)
            document.location.hash = ""
          else if (document.location.hash.drop(1) != pstr)
            window.history.pushState(0, ps.unit.fold("ffbecalc")("ffbecalc" + _), "#" + pstr)
        }
      case None =>
    }

    def loadFromHash(): Unit = {
      val ps = PageState.from(document.location.hash.drop(1))

      unitIdSubject.next(ps.unit.map(_.toString))
      esperIdSubject.next(ps.esper.map(_.toString))
      esperRaritySubject.next(ps.esperRarity)

      def s(i: Option[Int]): Option[String] = i.map(_.toString)
      abilitySubjects.a1.next(s(ps.mat1))
      abilitySubjects.a2.next(s(ps.mat2))
      abilitySubjects.a3.next(s(ps.mat3))
      abilitySubjects.a4.next(s(ps.mat4))
      acc1Subject.next(s(ps.acc1))
      acc2Subject.next(s(ps.acc2))
      headSubject.next(s(ps.head))
      bodySubject.next(s(ps.body))
      rhandSubject.next(s(ps.rhand))
      lhandSubject.next(s(ps.lhand))

      ps.enhs.toList.foreach(enhSubject.next)
    }

    def updateCheck(): Observable[String] = {
      import scala.concurrent.ExecutionContext.Implicits.global
      import org.scalajs.dom.ext.Ajax
      // a query string to bypass cdn
      val tag = System.currentTimeMillis / (5 * 60 * 1000)
      Observable.from(Ajax.get(url = s"versionCode?$tag",
        responseType = "text",
        headers = Map("Content-Type" -> "text/plain"))).map(_.responseText.trim)
    }
    val onLoad = outwatch.Sink.create[org.scalajs.dom.raw.Element] { e =>
      var subscription = Option.empty[rxscalajs.subscription.Subscription]
      window.addEventListener("popstate",
        { e: org.scalajs.dom.PopStateEvent =>
          subscription.foreach(_.unsubscribe)
          loadFromHash()
          subscription = Some(subscribeChanges)
        }, true)
      loadFromHash()
      subscription = Some(subscribeChanges)

      Observable.just(1).concat(Observable.interval(5.minutes)).flatMap(_ =>
        updateCheck()).dropWhile { (s,_) =>
          BuildInfo.versionCode == s }.take(1) { _ =>
        val reloadClick = createHandler[Unit]()
        reloadClick { _ =>
          document.location.reload(true)
          val node = document.getElementById("new-update")
          node.parentNode.removeChild(node)
        }
        OutWatch.render("#content",
          div(id := "new-update",
            span("A new version of ffbecalc is available"),
            button("REFRESH", click(()) --> reloadClick)))
      }
    }

    val resetClick = createHandler[Unit]()
    resetClick { _ =>
      window.history.pushState(0, "reload", "#")
      document.location.reload()
    }
    OutWatch.render("#content",
      div(insert --> onLoad,
        div(id := "unit-info",
          select(children <-- unitIndex, inputId --> unitIdSink),
          div(hidden <-- unitId.map(_.isEmpty),
            components.unitStats(unitInfo, unitEntry, unitStats, equipped, allPassives.map(_._2), esper, esperStats, esperEntry, enhancedSkills, enhMap), button("Reset", click(()) --> resetClick)
          )
        ),
        div(hidden <-- unitId.map(_.isEmpty),
        p(children <-- unitDescription.combineLatest(unitInfo).map { case (d,i) =>
          val eid = i.flatMap(_.entries.toList.sortBy(_._2.rarity).lastOption.map(_._1))
          eid.toList.map(id =>
            img(src := s"http://exviusdb.com/static/img/assets/unit/unit_ills_$id.png", align := "right")) ++ List(p(d))
        }),
        h3("Base Stats"),
        div(child <-- components.unitBaseStats(unitEntry, unitStats, pots)),
        h3("Equipment"),
        components.sortBy(_sorting),
        table(id := "equip-slots",
          tr(
            eqslot("Right Hand", Set(1, 2), rhandValidator, rhand, rhandId),
            eqslot("Left Hand",  Set(1, 2), lhandValidator, lhand, lhandId),
          ),
          tr(
            eqslot("Head", Set(3), equipsValidator(headSubject, _.head), headEquip, headId),
            eqslot("Body", Set(4), equipsValidator(bodySubject, _.body), bodyEquip, bodyId),
          ),
          tr(
            eqslot("Accessory 1", Set(5), equipsValidator(acc1Subject, _.acc1), acc1, acc1Id),
            eqslot("Accessory 2", Set(5), equipsValidator(acc2Subject, _.acc2), acc2, acc2Id),
          )
        ),
        h3("Materia"),
        table(id := "materia-slots",
          children <-- abilitySlots,
        ),
        h3("Esper"),
        Esper.esperInfo(esper, esperEntry, esperRaritySink, espers, esperIdSubject, esperRaritySubject, esperStats, esperSkills, esperTraining, esperTrainingSubject),
        h3("Abilities & Spells"),
        div(child <-- activesTable),
        h3("Traits"),
        div(child <-- traitsTable),
        div(hidden <-- equipSkills.map(_.isEmpty),
          h3("Equipped"),
          div(child <-- equippedTable),
        ),
        ),
      )
    )
  }
}
